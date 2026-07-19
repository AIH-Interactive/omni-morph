# YSM phase 4.1 regression resources

These files are intentionally tiny function-package fixtures for the native YSM runtime.

- `calc_flag.molang` covers ordinary `fn.calc_flag(...)` calls.
- `main@player_ctrl_parallel_1.molang` covers function plus controller slot event registration.
- `@player_ctrl_pre_main.molang` covers pure event files.
- `eventsubscriber@sync.molang` covers `ysm.sync(...)` dispatch with `args`.

They do not require a full Minecraft client and can be wired into a future unit or integration test task.
