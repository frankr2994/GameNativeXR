# GameNativeXR XR Bridge Protocol

Status: observed v0.4 contract and v0.5 design requirements

This document describes the XR bridge currently implemented by GameNativeXR.
It separates behavior verified in source from assumptions that still require a
probe, simulator trace, or native `libxr.so` source.

The immediate consumer is the planned GameNativeXR backend for Halo-MCC-VR.
Existing v0.1-v0.4 clients must continue working while a safer v0.5 protocol is
developed.

## 1. Scope

The bridge carries:

- headset and controller poses;
- controller axes and buttons;
- IPD and headset FOV values;
- a frame synchronization value;
- host immersive/SBS state;
- guest requests for VR, SBS, AER, and FOV;
- left/right haptic values.

It does not carry eye images. The Windows application renders into the
GameNativeXR/XServer display surface. The Android XR renderer samples that
surface and submits it to the native XR host.

## 2. Source of Truth

Current behavior is defined by:

- [`XrAPI.java`](../app/src/main/java/com/winlator/xr/api/XrAPI.java)
- [`XrInterface.java`](../app/src/main/java/com/winlator/xr/api/XrInterface.java)
- [`XrVersion01.java`](../app/src/main/java/com/winlator/xr/api/XrVersion01.java)
- [`XrVersion02.java`](../app/src/main/java/com/winlator/xr/api/XrVersion02.java)
- [`XrVersion03.java`](../app/src/main/java/com/winlator/xr/api/XrVersion03.java)
- [`XrVersion04.java`](../app/src/main/java/com/winlator/xr/api/XrVersion04.java)
- [`XrActivity.java`](../app/src/main/java/com/winlator/xr/XrActivity.java)
- [`XrRenderer.java`](../app/src/main/java/com/winlator/xr/XrRenderer.java)

`libxr.so` is also part of the effective contract because it supplies the
arrays consumed by `XrActivity`, owns OpenXR timing and actions, and receives
frame-sync values. Its source is not currently present, so units, coordinate
spaces, prediction behavior, and some rendering semantics remain unverified.

## 3. Roles

| Role | Current implementation |
|---|---|
| XR host | GameNativeXR Android process and native `libxr.so` |
| Guest | Windows application running inside GameNativeXR's Wine environment |
| Data direction | Host sends poses/buttons; guest sends mode/FOV/haptics |
| Image direction | Guest display surface is sampled by the Android XR renderer |

The terms "host" and "guest" in this document always use those meanings.

## 4. Discovery and Version Negotiation

The bridge directory is:

```text
/data/data/app.gamenative/files/imagefs/tmp/xr
```

On `XrAPI` construction, the host:

1. creates the directory if required;
2. attempts to delete every existing file in it;
3. writes a `system` file containing four ASCII lines:
   manufacturer, product, Android release, and security patch.

The guest opts in by writing a `version` file in that directory. The first line
selects the implementation by prefix:

| Prefix | Host implementation |
|---|---|
| `0.1` | `XrVersion01` |
| `0.2` | `XrVersion02` |
| `0.3` | `XrVersion03` |
| `0.4` | `XrVersion04` |

Unknown versions leave the bridge inactive. There is no host response,
capability exchange, timeout, or negotiated fallback.

The host only checks for the version file while it is not already using a
protocol implementation. Changing the file does not renegotiate an active
session.

## 5. Transport

The current transport is connectionless ASCII UDP over the local host.

| Direction | Purpose | v0.1-v0.3 | v0.4 |
|---|---|---:|---:|
| Host → guest | Pose, controller, headset, flags | UDP 7872 | UDP 7872 and 7873 |
| Guest → host | Haptics, modes, FOV | UDP 7278 | UDP 7278 |

In v0.4 the host sends the same packet to both ports 7872 and 7873. The current
Java source does not assign distinct semantics to the two ports.

Observed transport properties:

- destination is `InetAddress.getLocalHost()`;
- payload encoding is US-ASCII;
- receive buffer size is 1024 bytes;
- each listening port has a daemon thread;
- the receive loop sleeps 10 ms after processing a packet;
- there is no checksum, acknowledgement, retry, authentication, sequence
  number, timestamp, capability field, or explicit packet type;
- malformed numeric input is logged, but field count and finite values are not
  validated before indexing/storing.

These properties are compatibility facts, not v0.5 design recommendations.

## 6. Host-to-Guest Packet

### 6.1 Grammar

For v0.1-v0.4, the base packet is:

```text
client<index> <29 numeric fields> <19-character button field><optional flags>
```

The current host always sends client index `0`.

Whitespace between tokens is one ASCII space when produced by the host. A
guest parser should accept one or more ASCII whitespace characters between
tokens and must reject missing or extra fields explicitly.

### 6.2 Numeric Field Order

| # | Name | Host formatting |
|---:|---|---|
| 1 | `left.qx` | `%.3f` |
| 2 | `left.qy` | `%.3f` |
| 3 | `left.qz` | `%.3f` |
| 4 | `left.qw` | `%.3f` |
| 5 | `left.thumbstick_x` | `%.1f` |
| 6 | `left.thumbstick_y` | `%.1f` |
| 7 | `left.position_x` | `%.3f` |
| 8 | `left.position_y` | `%.3f` |
| 9 | `left.position_z` | `%.3f` |
| 10 | `right.qx` | `%.3f` |
| 11 | `right.qy` | `%.3f` |
| 12 | `right.qz` | `%.3f` |
| 13 | `right.qw` | `%.3f` |
| 14 | `right.thumbstick_x` | `%.1f` |
| 15 | `right.thumbstick_y` | `%.1f` |
| 16 | `right.position_x` | `%.3f` |
| 17 | `right.position_y` | `%.3f` |
| 18 | `right.position_z` | `%.3f` |
| 19 | `hmd.qx` | `%.3f` |
| 20 | `hmd.qy` | `%.3f` |
| 21 | `hmd.qz` | `%.3f` |
| 22 | `hmd.qw` | `%.3f` |
| 23 | `hmd.position_x` | `%.3f` |
| 24 | `hmd.position_y` | `%.3f` |
| 25 | `hmd.position_z` | `%.3f` |
| 26 | `hmd.ipd` | `%.4f` |
| 27 | `hmd.fov_x` | `%.2f` |
| 28 | `hmd.fov_y` | `%.2f` |
| 29 | `hmd.sync` | integer |

The pitch/yaw/roll entries present in `ControllerAxis` are not transmitted.

`Locale.US` is used, so decimal separators are periods.

### 6.3 Button Field

The button field is a contiguous string of 19 `T` or `F` characters:

| Character | Name |
|---:|---|
| 1 | `left.grip` |
| 2 | `left.menu` |
| 3 | `left.thumbstick_press` |
| 4 | `left.thumbstick_left` |
| 5 | `left.thumbstick_right` |
| 6 | `left.thumbstick_up` |
| 7 | `left.thumbstick_down` |
| 8 | `left.trigger` |
| 9 | `left.x` |
| 10 | `left.y` |
| 11 | `right.a` |
| 12 | `right.b` |
| 13 | `right.grip` |
| 14 | `right.thumbstick_press` |
| 15 | `right.thumbstick_left` |
| 16 | `right.thumbstick_right` |
| 17 | `right.thumbstick_up` |
| 18 | `right.thumbstick_down` |
| 19 | `right.trigger` |

The order is ABI-sensitive: the Java comments state that it must match
`libxr.so`.

### 6.4 v0.3/v0.4 Flags

Versions 0.3 and 0.4 append one space and two characters after the button
field:

```text
 <immersive><sbs>
```

Each flag is `T` or `F`.

| Character | Meaning |
|---:|---|
| 1 | Android host immersive mode is active |
| 2 | Android host SBS mode is active |

The flags describe current host state. Guest requests are carried in the
guest-to-host packet described below.

## 7. Guest-to-Host Packet

The packet received on UDP 7278 is a whitespace-separated list of six floats:

| # | Name | Observed consumer |
|---:|---|---|
| 1 | `left_haptics` | left controller haptic update |
| 2 | `right_haptics` | right controller haptic update |
| 3 | `mode_vr` | host VR/head-tracking/UDP behavior |
| 4 | `mode_3d` | flat/SBS/AER selection |
| 5 | `hmd_fov_x` | native host FOV update |
| 6 | `hmd_fov_y` | native host FOV update |

Current parsing behavior differs by version:

- v0.1 stores only values greater than zero;
- v0.2-v0.4 store values greater than zero for the two haptic fields and store
  any parsed value for fields three onward.

Consequences:

- a haptic field cannot be explicitly cleared to zero through the parser;
- negative haptic values are ignored;
- mode and FOV values can be set to zero in v0.2-v0.4;
- too many fields can index beyond the input array;
- too few fields leave previous values unchanged;
- `NaN` and infinity are not explicitly rejected.

After parsing a positive haptic value, `XrController.updateHaptics` calls
`vibrateController(1, channel, value)` and subtracts `0.1` from the stored value
on each update until it reaches zero. Values above 1 also select a different
external-haptics event name. The guest therefore requests a decaying pulse; it
does not directly control a persistent amplitude or send an explicit stop.

The Halo guest must initially send all six fields on every update, use finite
values, and avoid depending on zero as an immediate haptic stop.

## 8. Observed Mode Semantics

### 8.1 `mode_vr`

`XrActivity` currently interprets `mode_vr` as follows:

| Value | UDP active | Native VR submission | Head tracking allowed |
|---:|---|---|---|
| 0 | no | no | yes |
| 1 | yes | yes | no |
| 2 | yes | no | no |
| 3 | yes | no | yes |
| other positive | yes | no | no |

Only values 0, 1, and 3 have clearly observable intent in Java source. The
meaning of value 2 and other positive values is not documented and must not be
relied upon.

### 8.2 `mode_3d`

When `mode_vr` enables UDP behavior, non-negative `mode_3d` values are applied:

| Value | Behavior |
|---:|---|
| 0 | SBS off, AER off |
| 1 | SBS on, AER off |
| 2 | SBS off, AER on |
| other non-negative | SBS off, AER off |
| negative | preserve previous mode |

The first Halo implementation should request mode 1 (SBS). Mode 2 (AER) is an
experimental optimization after SBS correctness is established.

### 8.3 v0.1 Filesystem Flags

Version 0.1 also observes `sbs` and `vr` files in the bridge directory. Versions
0.2-v0.4 use the numeric input array instead.

## 9. Frame Synchronization Pixel

When VR rendering is enabled, `XrActivity.processFramesync` reads the top-left
pixel of the guest drawable at `(0, 0)`.

The returned four bytes are assigned by Java as:

```text
byte 0 -> b
byte 1 -> g
byte 2 -> r
byte 3 -> a
```

The host:

1. learns a sorted mapping of observed red-channel values;
2. expects `(256 / 12) + 1`, or 22, unique mapping entries;
3. maps each red value to `index * 12`;
4. calls `nativeSetFramesync(r, g, b, a)`;
5. treats a changed mapped red value as a new frame;
6. treats blue greater than zero as eye index 1, otherwise eye index 0.

If a new red value appears after the mapping reaches 22 entries, the mapping is
cleared and relearned.

This is an implicit color-space compensation mechanism. Its exact guest-side
encoder and native-channel semantics are not present in this repository.
Before Halo integration, the bridge probe must establish:

- exact input pixel format and channel order;
- the 22 values emitted by the guest;
- whether the pixel survives DXVK, XServer, scaling, filtering, and color-space
  conversion unchanged enough to map;
- meanings of green and alpha in `libxr.so`;
- eye and frame behavior in SBS and AER.

## 10. Unknowns That Must Not Be Guessed

The following are not established by current Java source:

- units for controller and HMD positions;
- coordinate handedness and axis directions;
- reference space and origin;
- whether poses are predicted and for what display time;
- whether controller poses are grip, aim, or another action space;
- exact FOV representation and units;
- IPD units, although meters are likely;
- `hmd.sync` producer and semantics;
- the purpose of v0.4's second output port;
- haptic value units, range, duration, and reset behavior;
- `nativeSetFramesync` green/alpha semantics;
- frame lifetime and eye reuse rules inside `libxr.so`.

These require the native source, a known compatible guest implementation, or
controlled simulator traces. Likely conventions are not protocol facts.

## 11. v0.4 Compatibility Requirements

The Halo bridge implementation must:

1. write `0.4` to the version file only after its UDP receivers are ready;
2. accept host packets from either port without processing the same frame
   twice;
3. parse exactly 29 numeric fields and exactly 19 button characters;
4. accept optional v0.3/v0.4 flags;
5. reject malformed, non-finite, truncated, or oversized packets without
   changing the last good state;
6. maintain a receive timestamp locally and detect stale tracking;
7. send all six guest-to-host fields as finite values;
8. default to SBS and fail back to flat rendering safely;
9. avoid blocking, allocation, logging, or socket setup in Halo render hooks;
10. keep v0.4 parsing isolated behind the GameNativeXR backend.

The guest should treat both UDP pose ports as redundant until a verified
consumer establishes different semantics.

## 12. Simulator-First Validation Matrix

| ID | Test | Expected evidence |
|---|---|---|
| SIM-001 | Version file absent | No UDP listeners or host pose packets |
| SIM-002 | Version `0.4` created | Listener on 7278; identical host packet format on 7872/7873 |
| SIM-003 | Static HMD/controllers | Valid quaternions, stable positions, 19-character button field |
| SIM-004 | Scripted HMD translation | Identify position units and axis signs |
| SIM-005 | Scripted HMD rotation | Identify quaternion handedness and component signs |
| SIM-006 | Scripted controller poses | Determine grip/aim basis and left/right conventions |
| SIM-007 | Every button/axis individually | Produce an authoritative action mapping |
| SIM-008 | Guest `mode_vr` values 0/1/2/3 | Confirm Java/native state transitions |
| SIM-009 | Guest `mode_3d` values -1/0/1/2 | Confirm preserve/flat/SBS/AER behavior |
| SIM-010 | FOV sweep | Determine units, valid range, and native response |
| SIM-011 | Haptic sweep and zero reset | Determine range/duration and expose sticky-zero behavior |
| SIM-012 | Frame-sync color ladder | Recover the guest encoding and channel mapping |
| SIM-013 | SBS eye-pattern surface | Confirm eye order, split, crop, and scaling |
| SIM-014 | AER alternating pattern | Confirm eye reuse and frame-change behavior |
| SIM-015 | Packet loss/reorder/duplicates | Verify guest stale-state and duplicate suppression |
| SIM-016 | Guest restart | Recreate version file and bridge without restarting Android app if possible |
| SIM-017 | Host/session focus loss | Safe flat fallback and deterministic recovery |
| SIM-018 | Malformed and oversized packets | No crash, OOB access, or invalid native values |

The simulator can establish protocol and lifecycle behavior. Physical Quest
hardware remains required for latency, Adreno copy paths, memory, thermals, and
playability acceptance.

## 13. v0.5 Requirements

Version 0.5 should be specified and tested before implementation. It must
provide:

- explicit packet magic, type, protocol version, and payload length;
- capability negotiation and backward-compatible rejection;
- monotonically increasing sequence IDs;
- monotonic host timestamps;
- predicted display time and display period;
- explicit pose sample time and validity/tracking flags;
- named pose spaces and coordinate convention;
- per-eye pose and asymmetric FOV values;
- explicit units for every field;
- session state, focus, visibility, and should-render state;
- refresh rate and recommended eye dimensions;
- structured controller actions with active/changed state;
- haptic amplitude, frequency, duration, channel, and stop command;
- transport error and dropped-frame counters;
- bounded parsing with finite-value checks;
- a restart/reconnect state machine;
- golden packet vectors and cross-language tests.

The v0.5 data plane may use shared memory if v0.4 UDP profiling proves that it
materially reduces stale poses or overhead. A local control channel should
still own negotiation, lifecycle, and diagnostics. Transport must be chosen
from measurements, not assumed.

## 14. Next Implementation Step

Build a standalone Windows x64 bridge probe before modifying Halo-MCC-VR. The
probe should:

- create the version file;
- listen on 7872 and 7873;
- parse v0.4 packets into an immutable latest-state snapshot;
- send six-field state packets to 7278;
- display packet age, rate, duplicates, malformed count, and all fields;
- render deterministic flat, SBS, AER, and frame-sync test patterns;
- record compact JSONL traces suitable for simulator replay;
- contain no MCC, Steam, injection, MinHook, or Halo dependencies.

Results from the simulator matrix should update this document: verified
unknowns become normative v0.4 compatibility facts, while device-dependent
properties remain explicitly unverified.
