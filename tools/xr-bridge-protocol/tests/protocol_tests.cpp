#include "XrBridgeProtocol.h"

#include <cmath>
#include <cstdlib>
#include <iostream>
#include <string>

using gamenativexr::bridge::HostToGuestState;
using gamenativexr::bridge::ParseError;

namespace {
int failures = 0;

void Expect(bool condition, const char* description) {
    if (!condition) {
        ++failures;
        std::cerr << "FAIL: " << description << '\n';
    }
}

HostToGuestState NominalState() {
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

constexpr const char* kH2G001 = "client0 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000 0.000 1.000 0.000 0.000 0.000 0.0630 90.00 90.00 0 FFFFFFFFFFFFFFFFFFF TT";

void TestHostToGuestGoldenVectors() {
    const auto nominal = gamenativexr::bridge::ParseHostToGuest(kH2G001);
    Expect(nominal && nominal.state.clientIndex == 0, "H2G-001 accepts nominal packet");
    Expect(nominal.state.immersive && nominal.state.sbs, "H2G-001 parses TT flags");
    Expect(std::fabs(nominal.state.numeric[25] - 0.063f) < 0.0001f, "H2G-001 parses IPD");
    Expect(gamenativexr::bridge::SerializeHostToGuest(NominalState()) == kH2G001, "H2G-001 serializes exact host format");

    constexpr const char* active = "client0 0.707 0.000 0.000 0.707 -0.5 0.8 -0.150 1.200 -0.450 0.000 0.707 0.000 0.707 0.9 -1.0 0.150 1.200 -0.450 0.000 0.000 0.383 0.924 0.000 1.650 -0.100 0.0655 105.50 95.25 142 FFFFFFFFFFFFFFFFFFF TT";
    const auto transforms = gamenativexr::bridge::ParseHostToGuest(active);
    Expect(transforms && transforms.state.sync == 142, "H2G-002 parses sync integer");
    Expect(transforms && std::fabs(transforms.state.numeric[7] - 1.2f) < 0.0001f, "H2G-002 preserves transforms");

    constexpr const char* alternating = "client0 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000 0.000 1.000 0.000 0.000 0.000 0.0630 90.00 90.00 0 TFTFTFTFTFTFTFTFTFT FF";
    const auto buttons = gamenativexr::bridge::ParseHostToGuest(alternating);
    Expect(buttons && buttons.state.buttons[0] && !buttons.state.buttons[1] && buttons.state.buttons[18], "H2G-004 parses button order");
    Expect(buttons && !buttons.state.immersive && !buttons.state.sbs, "H2G-004 parses FF flags");

    constexpr const char* whitespace = "client0   0.000  0.000 0.000 1.000   0.0 0.0  0.000 0.000 0.000  0.000 0.000 0.000 1.000  0.0 0.0  0.000 0.000 0.000  0.000 0.000 0.000 1.000  0.000 0.000 0.000  0.0630  90.00  90.00  0  FFFFFFFFFFFFFFFFFFF  TT";
    Expect(static_cast<bool>(gamenativexr::bridge::ParseHostToGuest(whitespace)), "H2G-006 accepts normalized ASCII whitespace");

    constexpr const char* truncated = "client0 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000 0.000 1.000 0.0 0.0 0.000 0.000 0.000 0.000 0.000";
    Expect(gamenativexr::bridge::ParseHostToGuest(truncated).error == ParseError::FieldCount, "H2G-007 rejects truncation");
    std::string invalidNumber(kH2G001);
    invalidNumber.replace(8, 5, "BAD_FLOAT");
    Expect(gamenativexr::bridge::ParseHostToGuest(invalidNumber).error == ParseError::InvalidNumber, "H2G-008 rejects invalid number");
    Expect(gamenativexr::bridge::ParseHostToGuest(std::string(kH2G001) + " EXTRA").error == ParseError::FieldCount, "H2G-009 rejects extra field");
    std::string invalidHeader(kH2G001);
    invalidHeader.replace(0, 7, "server0");
    Expect(gamenativexr::bridge::ParseHostToGuest(invalidHeader).error == ParseError::InvalidHeader, "H2G-010 rejects malformed header");

    std::string invalidButtons(kH2G001);
    invalidButtons.replace(invalidButtons.find("FFFFFFFFFFFFFFFFFFF"), 1, "X");
    Expect(gamenativexr::bridge::ParseHostToGuest(invalidButtons).error == ParseError::InvalidButtons, "strict parser rejects invalid button character");
    std::string invalidFlags(kH2G001);
    invalidFlags.replace(invalidFlags.size() - 1, 1, "X");
    Expect(gamenativexr::bridge::ParseHostToGuest(invalidFlags).error == ParseError::InvalidFlags, "strict parser rejects invalid flag character");
    std::string nonAscii(kH2G001);
    nonAscii[8] = static_cast<char>(0x80);
    Expect(gamenativexr::bridge::ParseHostToGuest(nonAscii).error == ParseError::NonAscii, "strict parser rejects non-ASCII host packet");
    Expect(gamenativexr::bridge::ParseHostToGuest("").error == ParseError::EmptyPacket, "strict parser rejects empty host packet");

    std::string maxSync(kH2G001);
    maxSync.replace(maxSync.rfind(" 0 F"), 2, " 2147483647");
    const auto maxSyncResult = gamenativexr::bridge::ParseHostToGuest(maxSync);
    Expect(maxSyncResult && maxSyncResult.state.sync == INT32_MAX, "strict parser accepts maximum int32 sync");
    std::string negativeSync(kH2G001);
    negativeSync.replace(negativeSync.rfind(" 0 F"), 2, " -1");
    Expect(gamenativexr::bridge::ParseHostToGuest(negativeSync).error == ParseError::InvalidSync, "strict parser rejects negative sync");
    std::string overflowSync(kH2G001);
    overflowSync.replace(overflowSync.rfind(" 0 F"), 2, " 2147483648");
    Expect(gamenativexr::bridge::ParseHostToGuest(overflowSync).error == ParseError::InvalidSync, "strict parser rejects overflow sync");
}

void TestGuestToHostGoldenVectors() {
    const auto nominal = gamenativexr::bridge::ParseGuestToHost("0.50 0.50 1 1 90.00 90.00");
    Expect(nominal && nominal.state.modeVr == 1.0f && nominal.state.mode3d == 1.0f, "G2H-001 accepts nominal packet");
    const auto flat = gamenativexr::bridge::ParseGuestToHost("0.0 0.0 0 0 0.0 0.0");
    Expect(flat && flat.state.hmdFovX == 0.0f, "G2H-002 accepts flat packet");
    const auto negative = gamenativexr::bridge::ParseGuestToHost("-0.50 -1.00 1 -1 90.00 90.00");
    Expect(negative && negative.state.leftHaptics < 0.0f && negative.state.mode3d < 0.0f, "G2H-005/006 retain values for caller policy");
    Expect(static_cast<bool>(gamenativexr::bridge::ParseGuestToHost("   0.80   0.80   1   1   95.00   95.00   ")), "G2H-007 accepts whitespace");
    Expect(gamenativexr::bridge::ParseGuestToHost("0.40 0.40 1").error == ParseError::FieldCount, "G2H-008 rejects truncation atomically");
    Expect(gamenativexr::bridge::ParseGuestToHost("0.50 0.50 1 1 90.00 90.00 999.00").error == ParseError::FieldCount, "G2H-009 rejects oversize atomically");
    Expect(gamenativexr::bridge::ParseGuestToHost("0.50 BAD_FLOAT 1 1 90.00 90.00").error == ParseError::InvalidNumber, "G2H-010 rejects invalid number atomically");
    Expect(gamenativexr::bridge::ParseGuestToHost("NaN 0 1 1 90 90").error == ParseError::NonFiniteNumber, "strict parser rejects NaN");
    Expect(gamenativexr::bridge::ParseGuestToHost("").error == ParseError::EmptyPacket, "strict parser rejects empty guest packet");
    std::string nonAscii("0.5 0.5 1 1 90 90");
    nonAscii[0] = static_cast<char>(0x80);
    Expect(gamenativexr::bridge::ParseGuestToHost(nonAscii).error == ParseError::NonAscii, "strict parser rejects non-ASCII guest packet");

    const gamenativexr::bridge::GuestToHostState expected = {0.125f, 1.5f, 3.0f, -1.0f, 105.5f, 95.25f};
    const auto roundTrip = gamenativexr::bridge::ParseGuestToHost(gamenativexr::bridge::SerializeGuestToHost(expected));
    Expect(roundTrip && std::fabs(roundTrip.state.leftHaptics - expected.leftHaptics) < 0.00001f &&
               std::fabs(roundTrip.state.rightHaptics - expected.rightHaptics) < 0.00001f &&
               roundTrip.state.modeVr == expected.modeVr && roundTrip.state.mode3d == expected.mode3d &&
               std::fabs(roundTrip.state.hmdFovX - expected.hmdFovX) < 0.00001f &&
               std::fabs(roundTrip.state.hmdFovY - expected.hmdFovY) < 0.00001f,
           "guest serializer round-trips finite command state");
}

} // namespace

int main() {
    TestHostToGuestGoldenVectors();
    TestGuestToHostGoldenVectors();
    if (failures != 0) return EXIT_FAILURE;
    std::cout << "All XR bridge protocol tests passed\n";
    return EXIT_SUCCESS;
}
