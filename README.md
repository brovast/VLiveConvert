# VLiveConvert

vivo 双文件实况 → 单文件实况的字节级无损转换工具（Android）。

## 背景

vivo 相机拍的「实况」其实是两个文件：IMG_xxx.jpg + IMG_xxx.mp4。双文件在实际用起来痛点不少：

- 换机、网盘、第三方传输工具经常把 .mp4 落下，实况秒变普通照片
- 微信等聊天工具发出去实况直接失效
- 部分第三方相册 / 壁纸应用不认双文件

## 这是什么

VLiveConvert 把 vivo 双文件实况转成「单文件实况」——一个 .jpg 内嵌视频，等同于 vivo 相册「关闭实况」时自动合并的产物。转换后：

- ✅ vivo 相册仍识别为实况，按压可播放
- ✅ 字节级无损：不重编码，EXIF（拍摄参数 / 镜头 / GPS / 拍摄时间）原样保留
- ✅ 标准 Google Motion Photo 结构，支持单文件动态照片的第三方相册也能读

## 功能

- 📷 内置选择器：自动扫描相册，只列出双文件实况照片（普通实况、人像实况都支持），普通照片不掺杂
- ⚡ 批量转换，4 路并发
<<<<<<< HEAD
- 🕐 修改时间对齐照片时间：导出文件的「修改时间」取自照片拍摄时间（EXIF / 文件名）；历史已转换文件可在主界面「修复时间」中多选批量修正
- 🗑️ 可选「转换后删除原图」：原 .jpg + 伴生 .mp4 一并处理（可选静默删除或系统回收站）
- 📦 APK 仅 2.5MB，全程本地处理，不联网、不上传任何数据
=======
- 🗑️ 可选「转换后删除原图」：原 .jpg + 伴生 .mp4 一并处理（可选静默删除或系统回收站）
- 📦 APK 仅 2.3MB，全程本地处理，不联网、不上传任何数据
=======

## 使用流程

1. 首次启动授予「照片和视频」权限（视频权限必须，否则找不到伴生 MP4）
2. 内置选择器自动扫描相册，只显示双文件实况照片
<<<<<<< HEAD
3. 选择照片 → 「开始转换」→ 单文件实况输出到相册 `Pictures/VLiveConvert`（修改时间与照片时间一致）
4. 可选开启「转换后删除原图」：转换成功后删除原 .jpg 与伴生 .mp4
   - 授予「所有文件访问权限」（推荐）：删除立即执行、无确认框，被删文件进入 vivo 相册「第三方删除拦截」，可在相册恢复
   - 不授权：每次删除前系统弹「移入回收站」确认框，文件以隐藏形式保留 30 天，可在本应用「恢复原图」入口恢复
5. 同名输出由 MediaStore 自动追加序号，不会覆盖已有文件
6. 历史转换的文件修改时间不对？主界面右上角「修复时间」→ 多选 → 一键按文件名时间修正
=======
3. 选择照片 → 「开始转换」→ 单文件实况输出到相册 `Pictures/VLiveConvert`
4. 可选开启「转换后删除原图」：转换成功后删除原 .jpg 与伴生 .mp4
   - 授予「所有文件访问权限」：删除立即执行，无任何确认框（**不进回收站，无法恢复**）
   - 不授权：每次删除前系统弹「移入回收站」确认框（确认后 30 天内可在相册回收站恢复）
5. 同名输出由 MediaStore 自动追加序号，不会覆盖已有文件
=======

## 已知说明

- 需要 Android 14 及以上（OriginOS 5 / 6 机型）
- 无损转换已在 X200 Ultra 的普通实况 + 人像实况真实样本上验证通过；
- 转换后能否被 vivo 相册识别为实况，欢迎反馈「机型 + 系统版本 + 结果」

## 转换原理（无损转换，不重编码）

输出结构（与 vivo 相册「关闭实况」合并产物逐字节对齐，经真机实测可被识别）：

```
[JPEG 主体 Primary + GainMap][streamdata 附加块][MP4 视频流][lpex box][convert footer]
```

关键处理：

| 步骤               | 说明                                                                                                                                                                                      |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 视频 uuid box 处理 | 保留`vivoMediaEStream`（旧机型实况标识，X200 Ultra 起已无）；剥掉 `vivoMediaExtInfo`（内嵌的源 footer 包装，与尾部 convert footer 重复会破坏识别）                                    |
| streamdata 附加块  | 源 JPG 中紧跟图像数据的 vivo 私有流，原样透传：普通实况约 114B（DEGS 流信息）；人像实况约 4MB（IAC 深度/虚化数据，丢失则相册不显示人像徽标、无法后编辑光圈/虚化）                         |
| lpex box           | 向`moov` 插入 LivePhotoExtension box（含封面帧时间戳等），并修复 stco/co64 偏移；缺 lpex 不被识别                                                                                       |
| convert footer     | 尾部追加`cameralbum!` footer（`com.vivo.gallery.file.convert=10004` 等），透传 imageTime，并合并源 JPG/MP4 footer 的全部附加字段（人像: `moduleid=portrait`、`joint.refocus` 等） |
| XMP                | Google Container（Primary/GainMap/MotionPhoto 视频项）+ VCamera 私有字段，`GCamera:MotionPhoto="1"`                                                                                     |
| 封面帧             | 由源 footer 的 imageTime（帧序号）按视频 fps 反推 PTS                                                                                                                                     |

## 双文件实况识别（内置选择器）

判定链（从严，避免误收录）：

1. `.jpg/.jpeg` 扩展名
2. 同目录存在同名 `.mp4` 且非空
3. XMP 无 `MotionPhoto`/`MicroVideo` 内嵌标记（排除 Google/OPPO/小米等单文件内嵌格式）
4. JPG 尾部能解析出含 28 字符 livephoto ID 的 `cameralbum!` footer

footer 尾部长度可变：普通实况 ID 字段 28 字符（tail 43B）；vivo X200 Ultra 人像实况
ID 字段前多 12 字节二进制（tail 55B，len2 字段语义已变）。解析器按 len2 试探、
失败回退「尾部直取到文件尾」，以末尾 FF 分隔 + magic 校验兜底。

注：X200 Ultra 双文件 MP4 已不含 `vivoMediaEStream` uuid box（旧 iQOO 格式才有）；
识别核心为 lpex box + convert footer + XMP。

## 代码结构

```
app/src/main/java/com/vliveconvert/app/
├── MainActivity.kt          # 权限流、选择器集成、转换调度、MediaStore 导出
├── core/                    # 无损核心（无 Android 依赖，可 JVM 单测）
│   ├── BinaryUtils/JpegUtil/Mp4Util/FooterUtil   # 大小端、JPEG 段、ISOBMFF box、cameralbum footer
│   ├── XmpTemplate.kt       # vivo 单文件实况 XMP 模板（逐字取自真机样本）+ XMP 定位
│   ├── LivePhotoAsset.kt    # 动态照片规范化表示
│   ├── VivoDual.kt          # 双文件识别 + 解析
│   └── VivoSingle.kt        # 单文件写出（uuid 剥离、lpex 合成、footer/XMP 拼装）
├── convert/Converter.kt     # 管线：detect → read → write
├── picker/                  # 内置选择器：MediaStore 直查 + 会话化多线程扫描（只收录双文件）
└── ui/                      # Compose 界面：主列表 / 权限引导 / 共享组件
```
