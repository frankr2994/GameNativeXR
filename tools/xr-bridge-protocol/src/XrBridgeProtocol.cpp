#include "XrBridgeProtocol.h"

#include <charconv>
#include <cmath>
#include <cstdlib>
#include <iomanip>
#include <limits>
#include <locale>
#include <sstream>
#include <vector>

namespace gamenativexr::bridge {
namespace {

bool IsAsciiWhitespace(char value) {
    return value == ' ' || value == '\t' || value == '\n' || value == '\r' || value == '\f' || value == '\v';
}

bool IsAscii(std::string_view value) {
    for (unsigned char character : value) {
        if (character > 0x7f) return false;
    }
    return true;
}

std::vector<std::string_view> SplitAsciiWhitespace(std::string_view value) {
    std::vector<std::string_view> tokens;
    std::size_t cursor = 0;
    while (cursor < value.size()) {
        while (cursor < value.size() && IsAsciiWhitespace(value[cursor])) ++cursor;
        const std::size_t start = cursor;
        while (cursor < value.size() && !IsAsciiWhitespace(value[cursor])) ++cursor;
        if (start != cursor) tokens.push_back(value.substr(start, cursor - start));
    }
    return tokens;
}

bool ParseFiniteFloat(std::string_view token, float& value, ParseError& error) {
    std::string owned(token);
    char* end = nullptr;
    value = std::strtof(owned.c_str(), &end);
    if (end == owned.c_str() || *end != '\0') {
        error = ParseError::InvalidNumber;
        return false;
    }
    if (!std::isfinite(value)) {
        error = ParseError::NonFiniteNumber;
        return false;
    }
    return true;
}

bool ParseClientIndex(std::string_view token, std::int32_t& value) {
    constexpr std::string_view prefix = "client";
    if (token.substr(0, prefix.size()) != prefix || token.size() == prefix.size()) return false;
    const auto result = std::from_chars(token.data() + prefix.size(), token.data() + token.size(), value);
    return result.ec == std::errc{} && result.ptr == token.data() + token.size() && value >= 0;
}

bool IsFlagString(std::string_view token, std::size_t expectedLength) {
    if (token.size() != expectedLength) return false;
    for (char value : token) {
        if (value != 'T' && value != 'F') return false;
    }
    return true;
}

} // namespace

const char* ToString(ParseError error) {
    switch (error) {
        case ParseError::None: return "NONE";
        case ParseError::EmptyPacket: return "EMPTY_PACKET";
        case ParseError::NonAscii: return "NON_ASCII";
        case ParseError::FieldCount: return "FIELD_COUNT";
        case ParseError::InvalidHeader: return "INVALID_HEADER";
        case ParseError::InvalidNumber: return "INVALID_NUMBER";
        case ParseError::NonFiniteNumber: return "NON_FINITE_NUMBER";
        case ParseError::InvalidSync: return "INVALID_SYNC";
        case ParseError::InvalidButtons: return "INVALID_BUTTONS";
        case ParseError::InvalidFlags: return "INVALID_FLAGS";
    }
    return "UNKNOWN";
}

ParseResult<HostToGuestState> ParseHostToGuest(std::string_view packet) {
    ParseResult<HostToGuestState> result;
    if (packet.empty()) {
        result.error = ParseError::EmptyPacket;
        return result;
    }
    if (!IsAscii(packet)) {
        result.error = ParseError::NonAscii;
        return result;
    }
    const auto tokens = SplitAsciiWhitespace(packet);
    if (tokens.size() != kHostToGuestTokenCount) {
        result.error = ParseError::FieldCount;
        return result;
    }
    if (!ParseClientIndex(tokens[0], result.state.clientIndex)) {
        result.error = ParseError::InvalidHeader;
        return result;
    }
    for (std::size_t index = 0; index < 28; ++index) {
        if (!ParseFiniteFloat(tokens[index + 1], result.state.numeric[index], result.error)) return result;
    }
    std::int32_t sync = 0;
    const auto syncResult = std::from_chars(tokens[29].data(), tokens[29].data() + tokens[29].size(), sync);
    if (syncResult.ec != std::errc{} || syncResult.ptr != tokens[29].data() + tokens[29].size() || sync < 0) {
        result.error = ParseError::InvalidSync;
        return result;
    }
    result.state.sync = sync;
    result.state.numeric[28] = static_cast<float>(sync);
    if (!IsFlagString(tokens[30], result.state.buttons.size())) {
        result.error = ParseError::InvalidButtons;
        return result;
    }
    for (std::size_t index = 0; index < result.state.buttons.size(); ++index) result.state.buttons[index] = tokens[30][index] == 'T';
    if (!IsFlagString(tokens[31], 2)) {
        result.error = ParseError::InvalidFlags;
        return result;
    }
    result.state.immersive = tokens[31][0] == 'T';
    result.state.sbs = tokens[31][1] == 'T';
    return result;
}

ParseResult<GuestToHostState> ParseGuestToHost(std::string_view packet) {
    ParseResult<GuestToHostState> result;
    if (packet.empty()) {
        result.error = ParseError::EmptyPacket;
        return result;
    }
    if (!IsAscii(packet)) {
        result.error = ParseError::NonAscii;
        return result;
    }
    const auto tokens = SplitAsciiWhitespace(packet);
    if (tokens.size() != kGuestToHostTokenCount) {
        result.error = ParseError::FieldCount;
        return result;
    }
    const std::array<float*, kGuestToHostTokenCount> fields = {
        &result.state.leftHaptics, &result.state.rightHaptics, &result.state.modeVr,
        &result.state.mode3d, &result.state.hmdFovX, &result.state.hmdFovY,
    };
    for (std::size_t index = 0; index < fields.size(); ++index) {
        if (!ParseFiniteFloat(tokens[index], *fields[index], result.error)) return result;
    }
    return result;
}

std::string SerializeHostToGuest(const HostToGuestState& state) {
    std::ostringstream stream;
    stream.imbue(std::locale::classic());
    stream << "client" << state.clientIndex;
    for (std::size_t index = 0; index < state.numeric.size(); ++index) {
        stream << ' ';
        if (index < 4 || (index >= 6 && index <= 12) || (index >= 15 && index <= 24)) stream << std::fixed << std::setprecision(3);
        else if (index == 25) stream << std::fixed << std::setprecision(4);
        else if (index == 26 || index == 27) stream << std::fixed << std::setprecision(2);
        else if (index == 28) stream << state.sync;
        else stream << std::fixed << std::setprecision(1);
        if (index != 28) stream << state.numeric[index];
    }
    stream << ' ';
    for (bool button : state.buttons) stream << (button ? 'T' : 'F');
    stream << ' ' << (state.immersive ? 'T' : 'F') << (state.sbs ? 'T' : 'F');
    return stream.str();
}

std::string SerializeGuestToHost(const GuestToHostState& state) {
    std::ostringstream stream;
    stream.imbue(std::locale::classic());
    stream << std::setprecision(std::numeric_limits<float>::max_digits10)
           << state.leftHaptics << ' ' << state.rightHaptics << ' ' << state.modeVr << ' '
           << state.mode3d << ' ' << state.hmdFovX << ' ' << state.hmdFovY;
    return stream.str();
}

} // namespace gamenativexr::bridge
