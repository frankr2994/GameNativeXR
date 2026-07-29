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
    Expect(transforms && transforms.state.numeric[28] == 142.0f, "H2G-002 parses sync integer");
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
}

} // namespace

int main() {
    TestHostToGuestGoldenVectors();
    TestGuestToHostGoldenVectors();
    if (failures != 0) return EXIT_FAILURE;
    std::cout << "All XR bridge protocol tests passed\n";
    return EXIT_SUCCESS;
}
