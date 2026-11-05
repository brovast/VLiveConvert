# Third-party notices

本模块内置了以下 Apache-2.0 开源项目的源码（为兼容本项目的 Kotlin 2.2 编译链，
避免直接依赖 Kotlin 2.3 元数据的 Maven 产物）：

## Backdrop

- 项目：https://github.com/Kyant0/AndroidLiquidGlass
- 版本：1.0.6（Android-only 模块）
- 版权：Copyright 2025 Kyant
- 许可：Apache License 2.0（见 `LICENSE-BACKDROP`）
- 本地改动：
  - `LayerRecorder.kt` 的 context receiver 改为显式参数（Kotlin 2.2 兼容）
  - backport KMP 2.0.1 中 `exportedBackdrop` / `backdrop` 的坐标失效修复
  - 包名保持 `com.kyant.backdrop` 不变

## Shapes

- 项目：https://github.com/Kyant0/Shapes
- 版本：1.2.0
- 版权：Copyright 2025 Kyant
- 许可：Apache License 2.0（见 `LICENSE-SHAPES`）
- 包名保持 `com.kyant.shapes` 不变
