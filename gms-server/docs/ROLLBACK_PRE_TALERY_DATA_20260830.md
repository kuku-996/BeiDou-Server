# GMS083 回退到 TaleryMS 全量 data 移植前的版本

备份日期：2026-08-30（Asia/Shanghai）

本次回退保留了原有服务端源码和功能代码，只恢复运行时数据：

- `gms-server/wz` 恢复自 `gms-server/备份2wingms/wz-原本`
- `gms-server/wz-zh-CN` 恢复自 `gms-server/备份2wingms/wz-zh-CN`
- 客户端 `Data` 恢复自 `Data-ws`
- 客户端 `Kaentake.dll` 恢复到 Talery 全量 data 移植前版本

校验数量：

- 服务端 `wz`：55,443 个文件
- 服务端 `wz-zh-CN`：52 个文件
- 客户端 `Data`：55,487 个文件

本机仍保留 Talery 全量 data 及中断复制产生的目录备份，未删除。

GitHub 备份分支不重复上传 `src` 下的 42 MB 演示视频资源（GitHub Blob API 单文件限制），该资源不参与服务端运行，原文件仍保留在本机。
