# BiliHDPager

BiliHDPager is an LSPosed module that fixes broken multi-page switching in old Bilibili HD clients.

[中文](README.md)

## What It Does

Some Bilibili HD videos look like normal multi-page videos, but the old HD client handles them internally as season/collection videos. In those cases, only the first few pages may open, while later pages do nothing or leave the page selection stuck.

This module patches the playback page so those pages can be opened normally.

## Compatibility

- Target app: Bilibili HD
- Package name: `tv.danmaku.bilibilihd`
- Requires LSPosed or a compatible framework
- No launcher icon and no settings UI

### Supported Client Versions

| Bilibili HD version | Status | Notes |
| --- | --- | --- |
| `1.38.0` | Tested | Playback page, fullscreen page selector, and cache page selector verified |
| `1.47.0` | Tested | Playback page, fullscreen page selector, and cache page selector verified |
| Other versions | Best effort | The module will try to match obfuscated playback source, cache, and selector classes automatically, but support is not guaranteed |

Obfuscated class and method names can change between Bilibili HD releases. For unlisted versions, please include LSPosed logs and the client version when reporting issues.

This module only fixes multi-page switching. It does not unlock paid content, region-restricted content, quality limits, or account permissions.

## Which APK Should I Install?

| Your LSPosed version | Recommended APK |
| --- | --- |
| v2.1.0 (7769) or newer | `BiliHDPager-0.1.0-modern-api102.apk` |
| Older than v2.1.0 (7769) | `BiliHDPager-0.1.0-legacy.apk` |

Do not install both variants at the same time. If you are not sure which LSPosed version you use, check it in LSPosed first.

## Usage

1. Install the matching APK.
2. Enable the module in LSPosed.
3. Select Bilibili HD as the module scope.
4. Force stop and reopen Bilibili HD, or reboot if needed.
5. Test a video whose later pages previously failed to open.

## Reporting Issues

Please include:

- Bilibili HD version
- LSPosed version
- Whether you installed `legacy` or `modern-api102`
- The video link or BV id
- Exported LSPosed logs

For compatibility debugging, you may be asked to install a debug APK. Debug APKs write detailed diagnostics to the LSPosed log with the `BiliHDPager` tag.
