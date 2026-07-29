#include <windows.h>
#include <d3d11_1.h>
#include <dxgi1_2.h>
#include <wrl/client.h>

#include <openxr/openxr.h>
#include <openxr/openxr_platform.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <stdexcept>
#include <string>
#include <thread>
#include <vector>

using Microsoft::WRL::ComPtr;

namespace {

enum class Pattern { Stereo, Sbs, Aer };

struct Options {
    std::uint32_t frames = 600;
    Pattern pattern = Pattern::Stereo;
    bool selfTest = false;
    bool help = false;
};

const char* PatternName(Pattern pattern) {
    switch (pattern) {
        case Pattern::Stereo: return "stereo";
        case Pattern::Sbs: return "sbs";
        case Pattern::Aer: return "aer";
    }
    return "unknown";
}

const char* SessionStateName(XrSessionState state) {
    switch (state) {
        case XR_SESSION_STATE_UNKNOWN: return "UNKNOWN";
        case XR_SESSION_STATE_IDLE: return "IDLE";
        case XR_SESSION_STATE_READY: return "READY";
        case XR_SESSION_STATE_SYNCHRONIZED: return "SYNCHRONIZED";
        case XR_SESSION_STATE_VISIBLE: return "VISIBLE";
        case XR_SESSION_STATE_FOCUSED: return "FOCUSED";
        case XR_SESSION_STATE_STOPPING: return "STOPPING";
        case XR_SESSION_STATE_LOSS_PENDING: return "LOSS_PENDING";
        case XR_SESSION_STATE_EXITING: return "EXITING";
        default: return "UNRECOGNIZED";
    }
}

Pattern ParsePattern(const std::string& value) {
    if (value == "stereo") return Pattern::Stereo;
    if (value == "sbs") return Pattern::Sbs;
    if (value == "aer") return Pattern::Aer;
    throw std::runtime_error("Unknown pattern: " + value);
}

Options ParseOptions(int argc, char** argv) {
    Options options;
    for (int index = 1; index < argc; ++index) {
        const std::string argument = argv[index];
        if (argument == "--self-test") {
            options.selfTest = true;
        } else if (argument == "--help" || argument == "-h") {
            options.help = true;
        } else if (argument == "--frames" && index + 1 < argc) {
            const unsigned long parsed = std::stoul(argv[++index]);
            if (parsed == 0 || parsed > 1000000UL) throw std::runtime_error("--frames must be between 1 and 1000000");
            options.frames = static_cast<std::uint32_t>(parsed);
        } else if (argument == "--pattern" && index + 1 < argc) {
            options.pattern = ParsePattern(argv[++index]);
        } else {
            throw std::runtime_error("Unknown or incomplete argument: " + argument);
        }
    }
    return options;
}

void PrintUsage() {
    std::cout << "xr_visual_harness [--frames N] [--pattern stereo|sbs|aer] [--self-test]\n";
}

void CheckHr(HRESULT result, const char* operation) {
    if (FAILED(result)) throw std::runtime_error(std::string(operation) + " failed with HRESULT " + std::to_string(result));
}

class VisualHarness {
public:
    explicit VisualHarness(Options options) : options_(options) {}
    ~VisualHarness() { Shutdown(); }

    int Run() {
        CreateInstance();
        CreateSystemAndDevice();
        CreateSession();
        CreateSwapchain();

        std::cout << "Pattern: " << PatternName(options_.pattern) << ", target frames: " << options_.frames << '\n';
        const auto start = std::chrono::steady_clock::now();
        while (!exitLoop_) {
            PollEvents();
            if (sessionRunning_) {
                RenderFrame();
            } else {
                std::this_thread::sleep_for(std::chrono::milliseconds(10));
                if (std::chrono::steady_clock::now() - start > std::chrono::seconds(30) && renderedFrames_ == 0) {
                    throw std::runtime_error("Timed out waiting for an OpenXR READY session");
                }
            }
        }
        std::cout << "Rendered frames: " << renderedFrames_ << '\n';
        return renderedFrames_ > 0 ? 0 : 2;
    }

private:
    void CheckXr(XrResult result, const char* operation) const {
        if (XR_SUCCEEDED(result)) return;
        char text[XR_MAX_RESULT_STRING_SIZE]{};
        if (instance_ != XR_NULL_HANDLE) xrResultToString(instance_, result, text);
        throw std::runtime_error(std::string(operation) + " failed: " + (text[0] ? text : std::to_string(result)));
    }

    void CreateInstance() {
        std::uint32_t extensionCount = 0;
        CheckXr(xrEnumerateInstanceExtensionProperties(nullptr, 0, &extensionCount, nullptr), "xrEnumerateInstanceExtensionProperties(count)");
        std::vector<XrExtensionProperties> extensions(extensionCount, {XR_TYPE_EXTENSION_PROPERTIES});
        CheckXr(xrEnumerateInstanceExtensionProperties(nullptr, extensionCount, &extensionCount, extensions.data()), "xrEnumerateInstanceExtensionProperties");
        const bool hasD3d11 = std::any_of(extensions.begin(), extensions.end(), [](const XrExtensionProperties& extension) {
            return std::strcmp(extension.extensionName, XR_KHR_D3D11_ENABLE_EXTENSION_NAME) == 0;
        });
        if (!hasD3d11) throw std::runtime_error("Runtime does not expose XR_KHR_D3D11_enable");

        const char* enabledExtensions[] = {XR_KHR_D3D11_ENABLE_EXTENSION_NAME};
        XrInstanceCreateInfo createInfo{XR_TYPE_INSTANCE_CREATE_INFO};
        strcpy_s(createInfo.applicationInfo.applicationName, "GameNativeXR Visual Harness");
        createInfo.applicationInfo.applicationVersion = 1;
        strcpy_s(createInfo.applicationInfo.engineName, "GameNativeXR diagnostics");
        createInfo.applicationInfo.engineVersion = 1;
        createInfo.applicationInfo.apiVersion = XR_CURRENT_API_VERSION;
        createInfo.enabledExtensionCount = 1;
        createInfo.enabledExtensionNames = enabledExtensions;
        CheckXr(xrCreateInstance(&createInfo, &instance_), "xrCreateInstance");

        XrInstanceProperties properties{XR_TYPE_INSTANCE_PROPERTIES};
        CheckXr(xrGetInstanceProperties(instance_, &properties), "xrGetInstanceProperties");
        std::cout << "Runtime: " << properties.runtimeName << " "
                  << XR_VERSION_MAJOR(properties.runtimeVersion) << '.'
                  << XR_VERSION_MINOR(properties.runtimeVersion) << '.'
                  << XR_VERSION_PATCH(properties.runtimeVersion) << '\n';
    }

    void CreateSystemAndDevice() {
        XrSystemGetInfo systemInfo{XR_TYPE_SYSTEM_GET_INFO};
        systemInfo.formFactor = XR_FORM_FACTOR_HEAD_MOUNTED_DISPLAY;
        CheckXr(xrGetSystem(instance_, &systemInfo, &systemId_), "xrGetSystem");

        PFN_xrGetD3D11GraphicsRequirementsKHR getRequirements = nullptr;
        CheckXr(xrGetInstanceProcAddr(instance_, "xrGetD3D11GraphicsRequirementsKHR",
                                     reinterpret_cast<PFN_xrVoidFunction*>(&getRequirements)),
                "xrGetInstanceProcAddr(xrGetD3D11GraphicsRequirementsKHR)");
        XrGraphicsRequirementsD3D11KHR requirements{XR_TYPE_GRAPHICS_REQUIREMENTS_D3D11_KHR};
        CheckXr(getRequirements(instance_, systemId_, &requirements), "xrGetD3D11GraphicsRequirementsKHR");

        ComPtr<IDXGIFactory1> factory;
        CheckHr(CreateDXGIFactory1(IID_PPV_ARGS(&factory)), "CreateDXGIFactory1");
        for (UINT index = 0;; ++index) {
            ComPtr<IDXGIAdapter1> candidate;
            if (factory->EnumAdapters1(index, &candidate) == DXGI_ERROR_NOT_FOUND) break;
            DXGI_ADAPTER_DESC1 description{};
            CheckHr(candidate->GetDesc1(&description), "IDXGIAdapter1::GetDesc1");
            if (std::memcmp(&description.AdapterLuid, &requirements.adapterLuid, sizeof(LUID)) == 0) {
                adapter_ = candidate;
                std::wcout << L"D3D11 adapter: " << description.Description << L'\n';
                break;
            }
        }
        if (!adapter_) throw std::runtime_error("Could not find the D3D11 adapter required by the OpenXR runtime");

        const std::array<D3D_FEATURE_LEVEL, 4> levels = {
            D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_1, D3D_FEATURE_LEVEL_10_0,
        };
        D3D_FEATURE_LEVEL selectedLevel{};
        CheckHr(D3D11CreateDevice(adapter_.Get(), D3D_DRIVER_TYPE_UNKNOWN, nullptr, 0, levels.data(),
                                  static_cast<UINT>(levels.size()), D3D11_SDK_VERSION, &device_, &selectedLevel, &context_),
                "D3D11CreateDevice");
        if (selectedLevel < requirements.minFeatureLevel) throw std::runtime_error("Created D3D11 feature level is below the runtime requirement");
        CheckHr(context_.As(&context1_), "ID3D11DeviceContext1 query");
    }

    void CreateSession() {
        XrGraphicsBindingD3D11KHR binding{XR_TYPE_GRAPHICS_BINDING_D3D11_KHR};
        binding.device = device_.Get();
        XrSessionCreateInfo sessionInfo{XR_TYPE_SESSION_CREATE_INFO};
        sessionInfo.next = &binding;
        sessionInfo.systemId = systemId_;
        CheckXr(xrCreateSession(instance_, &sessionInfo, &session_), "xrCreateSession");

        XrReferenceSpaceCreateInfo spaceInfo{XR_TYPE_REFERENCE_SPACE_CREATE_INFO};
        spaceInfo.referenceSpaceType = XR_REFERENCE_SPACE_TYPE_LOCAL;
        spaceInfo.poseInReferenceSpace.orientation.w = 1.0f;
        CheckXr(xrCreateReferenceSpace(session_, &spaceInfo, &space_), "xrCreateReferenceSpace(LOCAL)");

        std::uint32_t blendCount = 0;
        CheckXr(xrEnumerateEnvironmentBlendModes(instance_, systemId_, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                                  0, &blendCount, nullptr),
                "xrEnumerateEnvironmentBlendModes(count)");
        std::vector<XrEnvironmentBlendMode> modes(blendCount);
        CheckXr(xrEnumerateEnvironmentBlendModes(instance_, systemId_, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                                  blendCount, &blendCount, modes.data()),
                "xrEnumerateEnvironmentBlendModes");
        blendMode_ = std::find(modes.begin(), modes.end(), XR_ENVIRONMENT_BLEND_MODE_OPAQUE) != modes.end()
                         ? XR_ENVIRONMENT_BLEND_MODE_OPAQUE
                         : modes.at(0);
    }

    void CreateSwapchain() {
        std::uint32_t viewCount = 0;
        CheckXr(xrEnumerateViewConfigurationViews(instance_, systemId_, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                                   0, &viewCount, nullptr),
                "xrEnumerateViewConfigurationViews(count)");
        if (viewCount != 2) throw std::runtime_error("Harness requires exactly two PRIMARY_STEREO views");
        viewConfigs_.assign(viewCount, {XR_TYPE_VIEW_CONFIGURATION_VIEW});
        CheckXr(xrEnumerateViewConfigurationViews(instance_, systemId_, XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO,
                                                   viewCount, &viewCount, viewConfigs_.data()),
                "xrEnumerateViewConfigurationViews");

        std::uint32_t formatCount = 0;
        CheckXr(xrEnumerateSwapchainFormats(session_, 0, &formatCount, nullptr), "xrEnumerateSwapchainFormats(count)");
        std::vector<std::int64_t> formats(formatCount);
        CheckXr(xrEnumerateSwapchainFormats(session_, formatCount, &formatCount, formats.data()), "xrEnumerateSwapchainFormats");
        const std::array<std::int64_t, 3> preferred = {
            DXGI_FORMAT_R8G8B8A8_UNORM_SRGB, DXGI_FORMAT_R8G8B8A8_UNORM, DXGI_FORMAT_B8G8R8A8_UNORM,
        };
        for (const auto candidate : preferred) {
            if (std::find(formats.begin(), formats.end(), candidate) != formats.end()) {
                colorFormat_ = candidate;
                break;
            }
        }
        if (colorFormat_ == DXGI_FORMAT_UNKNOWN) throw std::runtime_error("Runtime exposes no supported RGBA8/BGRA8 swapchain format");

        width_ = viewConfigs_[0].recommendedImageRectWidth;
        height_ = viewConfigs_[0].recommendedImageRectHeight;
        XrSwapchainCreateInfo swapchainInfo{XR_TYPE_SWAPCHAIN_CREATE_INFO};
        swapchainInfo.usageFlags = XR_SWAPCHAIN_USAGE_COLOR_ATTACHMENT_BIT;
        swapchainInfo.format = colorFormat_;
        swapchainInfo.sampleCount = 1;
        swapchainInfo.width = width_;
        swapchainInfo.height = height_;
        swapchainInfo.faceCount = 1;
        swapchainInfo.arraySize = viewCount;
        swapchainInfo.mipCount = 1;
        CheckXr(xrCreateSwapchain(session_, &swapchainInfo, &swapchain_), "xrCreateSwapchain");

        std::uint32_t imageCount = 0;
        CheckXr(xrEnumerateSwapchainImages(swapchain_, 0, &imageCount, nullptr), "xrEnumerateSwapchainImages(count)");
        swapchainImages_.assign(imageCount, {XR_TYPE_SWAPCHAIN_IMAGE_D3D11_KHR});
        CheckXr(xrEnumerateSwapchainImages(swapchain_, imageCount, &imageCount,
                                           reinterpret_cast<XrSwapchainImageBaseHeader*>(swapchainImages_.data())),
                "xrEnumerateSwapchainImages");
        renderTargets_.resize(imageCount);
        for (std::uint32_t image = 0; image < imageCount; ++image) {
            renderTargets_[image].resize(viewCount);
            for (std::uint32_t eye = 0; eye < viewCount; ++eye) {
                D3D11_RENDER_TARGET_VIEW_DESC description{};
                description.Format = static_cast<DXGI_FORMAT>(colorFormat_);
                description.ViewDimension = D3D11_RTV_DIMENSION_TEXTURE2DARRAY;
                description.Texture2DArray.MipSlice = 0;
                description.Texture2DArray.FirstArraySlice = eye;
                description.Texture2DArray.ArraySize = 1;
                CheckHr(device_->CreateRenderTargetView(swapchainImages_[image].texture, &description,
                                                        &renderTargets_[image][eye]),
                        "ID3D11Device::CreateRenderTargetView");
            }
        }
        views_.assign(viewCount, {XR_TYPE_VIEW});
        projectionViews_.assign(viewCount, {XR_TYPE_COMPOSITION_LAYER_PROJECTION_VIEW});
        std::cout << "Swapchain: " << width_ << 'x' << height_ << ", array views: " << viewCount
                  << ", images: " << imageCount << ", DXGI format: " << colorFormat_ << '\n';
    }

    void PollEvents() {
        XrEventDataBuffer event{XR_TYPE_EVENT_DATA_BUFFER};
        while (xrPollEvent(instance_, &event) == XR_SUCCESS) {
            if (event.type == XR_TYPE_EVENT_DATA_SESSION_STATE_CHANGED) {
                const auto& changed = *reinterpret_cast<const XrEventDataSessionStateChanged*>(&event);
                sessionState_ = changed.state;
                std::cout << "Session state: " << SessionStateName(sessionState_)
                          << " (" << static_cast<int>(sessionState_) << ")\n";
                if (sessionState_ == XR_SESSION_STATE_READY) {
                    XrSessionBeginInfo beginInfo{XR_TYPE_SESSION_BEGIN_INFO};
                    beginInfo.primaryViewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
                    CheckXr(xrBeginSession(session_, &beginInfo), "xrBeginSession");
                    sessionRunning_ = true;
                } else if (sessionState_ == XR_SESSION_STATE_STOPPING) {
                    if (sessionRunning_) CheckXr(xrEndSession(session_), "xrEndSession");
                    sessionRunning_ = false;
                    if (exitRequested_) exitLoop_ = true;
                } else if (sessionState_ == XR_SESSION_STATE_EXITING || sessionState_ == XR_SESSION_STATE_LOSS_PENDING) {
                    exitLoop_ = true;
                }
            } else if (event.type == XR_TYPE_EVENT_DATA_INSTANCE_LOSS_PENDING) {
                exitLoop_ = true;
            }
            event = {XR_TYPE_EVENT_DATA_BUFFER};
        }
    }

    void RenderFrame() {
        XrFrameWaitInfo waitInfo{XR_TYPE_FRAME_WAIT_INFO};
        XrFrameState frameState{XR_TYPE_FRAME_STATE};
        CheckXr(xrWaitFrame(session_, &waitInfo, &frameState), "xrWaitFrame");
        XrFrameBeginInfo beginInfo{XR_TYPE_FRAME_BEGIN_INFO};
        CheckXr(xrBeginFrame(session_, &beginInfo), "xrBeginFrame");

        XrCompositionLayerProjection layer{XR_TYPE_COMPOSITION_LAYER_PROJECTION};
        std::vector<const XrCompositionLayerBaseHeader*> layers;
        if (frameState.shouldRender) {
            XrViewLocateInfo locateInfo{XR_TYPE_VIEW_LOCATE_INFO};
            locateInfo.viewConfigurationType = XR_VIEW_CONFIGURATION_TYPE_PRIMARY_STEREO;
            locateInfo.displayTime = frameState.predictedDisplayTime;
            locateInfo.space = space_;
            XrViewState viewState{XR_TYPE_VIEW_STATE};
            std::uint32_t locatedCount = 0;
            CheckXr(xrLocateViews(session_, &locateInfo, &viewState, static_cast<std::uint32_t>(views_.size()),
                                  &locatedCount, views_.data()),
                    "xrLocateViews");
            const XrViewStateFlags required = XR_VIEW_STATE_POSITION_VALID_BIT | XR_VIEW_STATE_ORIENTATION_VALID_BIT;
            if (locatedCount == views_.size() && (viewState.viewStateFlags & required) == required) {
                std::uint32_t imageIndex = 0;
                XrSwapchainImageAcquireInfo acquireInfo{XR_TYPE_SWAPCHAIN_IMAGE_ACQUIRE_INFO};
                CheckXr(xrAcquireSwapchainImage(swapchain_, &acquireInfo, &imageIndex), "xrAcquireSwapchainImage");
                XrSwapchainImageWaitInfo imageWait{XR_TYPE_SWAPCHAIN_IMAGE_WAIT_INFO};
                imageWait.timeout = XR_INFINITE_DURATION;
                CheckXr(xrWaitSwapchainImage(swapchain_, &imageWait), "xrWaitSwapchainImage");
                for (std::uint32_t eye = 0; eye < views_.size(); ++eye) RenderEye(imageIndex, eye, renderedFrames_);
                context_->Flush();
                XrSwapchainImageReleaseInfo releaseInfo{XR_TYPE_SWAPCHAIN_IMAGE_RELEASE_INFO};
                CheckXr(xrReleaseSwapchainImage(swapchain_, &releaseInfo), "xrReleaseSwapchainImage");

                for (std::uint32_t eye = 0; eye < projectionViews_.size(); ++eye) {
                    projectionViews_[eye].pose = views_[eye].pose;
                    projectionViews_[eye].fov = views_[eye].fov;
                    projectionViews_[eye].subImage.swapchain = swapchain_;
                    projectionViews_[eye].subImage.imageRect = {{0, 0}, {static_cast<std::int32_t>(width_), static_cast<std::int32_t>(height_)}};
                    projectionViews_[eye].subImage.imageArrayIndex = eye;
                }
                layer.space = space_;
                layer.viewCount = static_cast<std::uint32_t>(projectionViews_.size());
                layer.views = projectionViews_.data();
                layers.push_back(reinterpret_cast<const XrCompositionLayerBaseHeader*>(&layer));
                ++renderedFrames_;
            }
        }

        XrFrameEndInfo endInfo{XR_TYPE_FRAME_END_INFO};
        endInfo.displayTime = frameState.predictedDisplayTime;
        endInfo.environmentBlendMode = blendMode_;
        endInfo.layerCount = static_cast<std::uint32_t>(layers.size());
        endInfo.layers = layers.empty() ? nullptr : layers.data();
        CheckXr(xrEndFrame(session_, &endInfo), "xrEndFrame");

        if (!exitRequested_ && renderedFrames_ >= options_.frames) {
            CheckXr(xrRequestExitSession(session_), "xrRequestExitSession");
            exitRequested_ = true;
        }
    }

    void RenderEye(std::uint32_t imageIndex, std::uint32_t eye, std::uint64_t frame) {
        const std::array<float, 4> left = {0.03f, 0.12f, 0.65f, 1.0f};
        const std::array<float, 4> right = {0.70f, 0.06f, 0.04f, 1.0f};
        const std::array<float, 4> white = {0.90f, 0.90f, 0.90f, 1.0f};
        const std::array<float, 4> green = {0.05f, 0.90f, 0.15f, 1.0f};
        const std::array<float, 4> dark = {0.01f, 0.01f, 0.01f, 1.0f};
        ID3D11RenderTargetView* target = renderTargets_[imageIndex][eye].Get();

        std::array<float, 4> background = eye == 0 ? left : right;
        if (options_.pattern == Pattern::Aer && ((frame + eye) & 1U) != 0U) {
            background = {0.02f, 0.02f, 0.02f, 1.0f};
        }
        context_->ClearRenderTargetView(target, background.data());

        if (options_.pattern == Pattern::Sbs) {
            const D3D11_RECT leftHalf = {0, 0, static_cast<LONG>(width_ / 2), static_cast<LONG>(height_)};
            const D3D11_RECT rightHalf = {static_cast<LONG>(width_ / 2), 0, static_cast<LONG>(width_), static_cast<LONG>(height_)};
            context1_->ClearView(target, left.data(), &leftHalf, 1);
            context1_->ClearView(target, right.data(), &rightHalf, 1);
        }

        const LONG centerX = static_cast<LONG>(width_ / 2);
        const LONG centerY = static_cast<LONG>(height_ / 2);
        const LONG thickness = std::max<LONG>(2, static_cast<LONG>(std::min(width_, height_) / 300));
        const D3D11_RECT cross[] = {
            {centerX - thickness, static_cast<LONG>(height_ / 4), centerX + thickness, static_cast<LONG>(height_ * 3 / 4)},
            {static_cast<LONG>(width_ / 4), centerY - thickness, static_cast<LONG>(width_ * 3 / 4), centerY + thickness},
        };
        context1_->ClearView(target, white.data(), cross, 2);

        const LONG markerSize = std::max<LONG>(8, static_cast<LONG>(width_ / 80));
        for (std::uint32_t bit = 0; bit < 8; ++bit) {
            const LONG x = markerSize * static_cast<LONG>(bit + 1);
            const D3D11_RECT marker = {x, markerSize, x + markerSize - 2, markerSize * 2};
            const auto& color = ((frame >> bit) & 1U) != 0U ? green : dark;
            context1_->ClearView(target, color.data(), &marker, 1);
        }
    }

    void Shutdown() noexcept {
        if (swapchain_ != XR_NULL_HANDLE) xrDestroySwapchain(swapchain_);
        swapchain_ = XR_NULL_HANDLE;
        if (space_ != XR_NULL_HANDLE) xrDestroySpace(space_);
        space_ = XR_NULL_HANDLE;
        if (session_ != XR_NULL_HANDLE) xrDestroySession(session_);
        session_ = XR_NULL_HANDLE;
        if (instance_ != XR_NULL_HANDLE) xrDestroyInstance(instance_);
        instance_ = XR_NULL_HANDLE;
    }

    Options options_;
    XrInstance instance_ = XR_NULL_HANDLE;
    XrSystemId systemId_ = XR_NULL_SYSTEM_ID;
    XrSession session_ = XR_NULL_HANDLE;
    XrSpace space_ = XR_NULL_HANDLE;
    XrSwapchain swapchain_ = XR_NULL_HANDLE;
    XrSessionState sessionState_ = XR_SESSION_STATE_UNKNOWN;
    XrEnvironmentBlendMode blendMode_ = XR_ENVIRONMENT_BLEND_MODE_OPAQUE;
    bool sessionRunning_ = false;
    bool exitRequested_ = false;
    bool exitLoop_ = false;
    std::uint64_t renderedFrames_ = 0;
    std::uint32_t width_ = 0;
    std::uint32_t height_ = 0;
    std::int64_t colorFormat_ = DXGI_FORMAT_UNKNOWN;
    ComPtr<IDXGIAdapter1> adapter_;
    ComPtr<ID3D11Device> device_;
    ComPtr<ID3D11DeviceContext> context_;
    ComPtr<ID3D11DeviceContext1> context1_;
    std::vector<XrViewConfigurationView> viewConfigs_;
    std::vector<XrSwapchainImageD3D11KHR> swapchainImages_;
    std::vector<std::vector<ComPtr<ID3D11RenderTargetView>>> renderTargets_;
    std::vector<XrView> views_;
    std::vector<XrCompositionLayerProjectionView> projectionViews_;
};

int SelfTest() {
    if (ParsePattern("stereo") != Pattern::Stereo || ParsePattern("sbs") != Pattern::Sbs ||
        ParsePattern("aer") != Pattern::Aer || std::string(PatternName(Pattern::Aer)) != "aer" ||
        std::string(SessionStateName(XR_SESSION_STATE_READY)) != "READY") {
        std::cerr << "Pattern self-test failed\n";
        return 1;
    }
    bool rejected = false;
    try {
        static_cast<void>(ParsePattern("invalid"));
    } catch (const std::runtime_error&) {
        rejected = true;
    }
    if (!rejected) {
        std::cerr << "Invalid pattern self-test failed\n";
        return 1;
    }
    std::cout << "Visual harness self-test passed\n";
    return 0;
}

} // namespace

int main(int argc, char** argv) {
    try {
        const Options options = ParseOptions(argc, argv);
        if (options.help) {
            PrintUsage();
            return 0;
        }
        if (options.selfTest) return SelfTest();
        return VisualHarness(options).Run();
    } catch (const std::exception& exception) {
        std::cerr << "xr_visual_harness: " << exception.what() << '\n';
        return 1;
    }
}
