# G0U Protocol Validation

Date: 2026-07-29
Repository: GameNativeXR `Dev-Update`
Commit under test: `7542aaba`

## Scope

This record qualifies the standalone Windows x64 protocol utility in
`tools/xr-bridge-protocol` after the GameNative upstream merge. It covers the
strict v0.4 parser/serializer and the local UDP mock-host round trip only.
It does not validate the Android APK, the native XR host, headset tracking,
image transport, or a physical Quest device.

## Commands and results

```powershell
cmake -S . -B out -G "Visual Studio 17 2022" -A x64
cmake --build out --config Release --parallel 1
ctest --test-dir out -C Release --output-on-failure
```

Result: configuration and Release x64 build succeeded with MSVC 19.31.31107.0.
CTest passed `xr_bridge_protocol_tests` (1/1 tests, 0 failures).

The test executable covers nominal vectors, parser/serializer round trips,
whitespace handling, invalid field counts, malformed values, non-finite values,
non-ASCII packets, button/flag validation, and frame-sync bounds for both
directions of the strict protocol contract.

## UDP loopback check

`xr_bridge_mock --mock-host 3000` was started on loopback UDP port `7278`.
A valid guest-to-host packet was sent:

```text
0.50 0.50 1 1 90.00 90.00
```

Receivers bound to loopback UDP ports `7872` and `7873` each received the same
193-byte, `client0`-prefixed host-to-guest packet. This confirms the mock host
accepts the nominal guest packet and emits its response on both documented
return ports.

## Conclusion

The desktop strict protocol mock meets the G0U protocol regression requirement
at commit `7542aaba`. Its result must not be treated as proof of Android host
behavior or physical Quest XR operation.
