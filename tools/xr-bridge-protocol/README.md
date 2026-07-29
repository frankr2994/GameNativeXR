# XR Bridge Protocol Mock

This is a Windows x64 development utility for the observed GameNativeXR v0.4
UDP contract. It is intentionally separate from the Android application and
does not render images, use OpenXR, or prove the Android XServer image path.

It provides:

- a strict host-to-guest parser and serializer for UDP `7872`/`7873`;
- a strict guest-to-host parser and serializer for UDP `7278`;
- a loopback mock host which accepts valid guest packets and emits a nominal
  host state to `7872` and `7873` on the sender address;
- CTest coverage based on the Antigravity golden-vector corpus.

Malformed packets are rejected atomically. This is deliberate: it does not
copy the current Android host's partial-update behavior for truncated,
overlong, or invalid packets.

## Build and test

From this directory on Windows with Visual Studio 2022 tools installed:

```powershell
cmake -S . -B out -G "Visual Studio 17 2022" -A x64
cmake --build out --config Release
ctest --test-dir out -C Release --output-on-failure
```

## Manual validation

```powershell
.\out\Release\xr_bridge_mock.exe --validate-guest-to-host "0.50 0.50 1 1 90.00 90.00"
.\out\Release\xr_bridge_mock.exe --mock-host 10000
```

The second command binds loopback UDP `7278` for ten seconds. When it receives
a valid six-field packet, it sends a nominal v0.4 host-state packet to ports
`7872` and `7873` on the sender. It never touches Android files, headset state,
or the system OpenXR runtime.

## Corpus provenance

The test cases are derived from the workspace handoff artifacts:

```text
F:\QuestVR\ANTIGRAVITY_HANDOFFS\TASK-01-protocol-vectors\
```

Those artifacts are design evidence, not a claim that the Android host parses
host-to-guest packets or that a Windows mock validates the Android image path.
