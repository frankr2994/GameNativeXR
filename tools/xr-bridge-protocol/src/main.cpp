#include "XrBridgeProtocol.h"

#include <chrono>
#include <cstdlib>
#include <iostream>
#include <string>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#endif

namespace {
using gamenativexr::bridge::GuestToHostState;
using gamenativexr::bridge::HostToGuestState;

HostToGuestState MakeNominalHostState() {
    HostToGuestState state;
    state.numeric[3] = 1.0f;
    state.numeric[12] = 1.0f;
    state.numeric[21] = 1.0f;
    state.numeric[25] = 0.063f;
    state.numeric[26] = 90.0f;
    state.numeric[27] = 90.0f;
    state.immersive = true;
    state.sbs = true;
    return state;
}

void PrintUsage() {
    std::cerr << "Usage:\n"
              << "  xr_bridge_mock --validate-host-to-guest <packet>\n"
              << "  xr_bridge_mock --validate-guest-to-host <packet>\n"
              << "  xr_bridge_mock --mock-host [duration-ms]\n";
}

#ifdef _WIN32
int RunMockHost(int durationMs) {
    WSADATA wsa{};
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        std::cerr << "WSAStartup failed\n";
        return 2;
    }
    SOCKET socketHandle = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (socketHandle == INVALID_SOCKET) {
        std::cerr << "socket failed\n";
        WSACleanup();
        return 2;
    }
    sockaddr_in bindAddress{};
    bindAddress.sin_family = AF_INET;
    bindAddress.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    bindAddress.sin_port = htons(gamenativexr::bridge::kGuestToHostPort);
    if (bind(socketHandle, reinterpret_cast<const sockaddr*>(&bindAddress), sizeof(bindAddress)) == SOCKET_ERROR) {
        std::cerr << "bind UDP 7278 failed (is another host already listening?)\n";
        closesocket(socketHandle);
        WSACleanup();
        return 2;
    }

    const std::string response = gamenativexr::bridge::SerializeHostToGuest(MakeNominalHostState());
    const auto deadline = std::chrono::steady_clock::now() + std::chrono::milliseconds(durationMs);
    std::cout << "Mock host listening on 127.0.0.1:7278 for " << durationMs << " ms\n";
    while (std::chrono::steady_clock::now() < deadline) {
        fd_set readSet;
        FD_ZERO(&readSet);
        FD_SET(socketHandle, &readSet);
        timeval timeout{0, 100000};
        if (select(0, &readSet, nullptr, nullptr, &timeout) <= 0) continue;

        char buffer[1025]{};
        sockaddr_in sender{};
        int senderSize = sizeof(sender);
        const int bytes = recvfrom(socketHandle, buffer, 1024, 0, reinterpret_cast<sockaddr*>(&sender), &senderSize);
        if (bytes <= 0) continue;
        const auto parsed = gamenativexr::bridge::ParseGuestToHost(std::string_view(buffer, static_cast<std::size_t>(bytes)));
        if (!parsed) {
            std::cout << "Rejected guest packet: " << gamenativexr::bridge::ToString(parsed.error) << "\n";
            continue;
        }
        for (const auto port : {gamenativexr::bridge::kHostToGuestPortPrimary, gamenativexr::bridge::kHostToGuestPortSecondary}) {
            sender.sin_port = htons(port);
            sendto(socketHandle, response.data(), static_cast<int>(response.size()), 0,
                   reinterpret_cast<const sockaddr*>(&sender), sizeof(sender));
        }
        std::cout << "Accepted guest packet; emitted host state to UDP 7872 and 7873\n";
    }
    closesocket(socketHandle);
    WSACleanup();
    return 0;
}
#endif

} // namespace

int main(int argc, char** argv) {
    if (argc < 2) {
        PrintUsage();
        return 1;
    }
    const std::string command = argv[1];
    if ((command == "--validate-host-to-guest" || command == "--validate-guest-to-host") && argc == 3) {
        if (command == "--validate-host-to-guest") {
            const auto parsed = gamenativexr::bridge::ParseHostToGuest(argv[2]);
            std::cout << (parsed ? "ACCEPT" : "REJECT") << ' ' << gamenativexr::bridge::ToString(parsed.error) << '\n';
            return parsed ? 0 : 3;
        }
        const auto parsed = gamenativexr::bridge::ParseGuestToHost(argv[2]);
        std::cout << (parsed ? "ACCEPT" : "REJECT") << ' ' << gamenativexr::bridge::ToString(parsed.error) << '\n';
        return parsed ? 0 : 3;
    }
    if (command == "--mock-host" && (argc == 2 || argc == 3)) {
        const int durationMs = argc == 3 ? std::atoi(argv[2]) : 10000;
        if (durationMs <= 0) {
            std::cerr << "duration-ms must be positive\n";
            return 1;
        }
#ifdef _WIN32
        return RunMockHost(durationMs);
#else
        std::cerr << "--mock-host currently requires Windows sockets\n";
        return 2;
#endif
    }
    PrintUsage();
    return 1;
}
