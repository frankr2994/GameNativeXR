#pragma once

#include <array>
#include <cstdint>
#include <string>
#include <string_view>

namespace gamenativexr::bridge {

constexpr std::uint16_t kGuestToHostPort = 7278;
constexpr std::uint16_t kHostToGuestPortPrimary = 7872;
constexpr std::uint16_t kHostToGuestPortSecondary = 7873;
constexpr std::size_t kHostToGuestTokenCount = 32;
constexpr std::size_t kGuestToHostTokenCount = 6;

enum class ParseError {
    None,
    EmptyPacket,
    NonAscii,
    FieldCount,
    InvalidHeader,
    InvalidNumber,
    NonFiniteNumber,
    InvalidSync,
    InvalidButtons,
    InvalidFlags,
};

const char* ToString(ParseError error);

struct HostToGuestState {
    std::int32_t clientIndex = 0;
    std::array<float, 29> numeric{};
    std::array<bool, 19> buttons{};
    bool immersive = false;
    bool sbs = false;
};

struct GuestToHostState {
    float leftHaptics = 0.0f;
    float rightHaptics = 0.0f;
    float modeVr = 0.0f;
    float mode3d = 0.0f;
    float hmdFovX = 0.0f;
    float hmdFovY = 0.0f;
};

template <typename State>
struct ParseResult {
    State state{};
    ParseError error = ParseError::None;

    explicit operator bool() const { return error == ParseError::None; }
};

// Strict parser for the new guest implementation. It deliberately does not
// reproduce the Android host's partial-update behavior on malformed packets.
ParseResult<HostToGuestState> ParseHostToGuest(std::string_view packet);
ParseResult<GuestToHostState> ParseGuestToHost(std::string_view packet);

std::string SerializeHostToGuest(const HostToGuestState& state);
std::string SerializeGuestToHost(const GuestToHostState& state);

} // namespace gamenativexr::bridge
