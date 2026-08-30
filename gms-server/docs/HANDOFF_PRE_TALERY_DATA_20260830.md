# GMS083 Kaentake + BeiDou 服务端交接文档

交接日期：2026-08-30（Asia/Shanghai）

本文件用于新 Codex 对话接手当前项目。当前决定：停止 TaleryMS 全量 data 移植，使用 TaleryMS data 移植前的 GMS083 运行时数据。

## 一、项目路径

客户端运行目录：
D:\0BEIDOUWZ\wingmsmxd

客户端源码：
D:\Codex\从0开始客户端\kaentake-official

客户端构建目录：
D:\Codex\从0开始客户端\kaentake-official\build-chat-ui-pch

客户端 DLL：
D:\0BEIDOUWZ\wingmsmxd\Kaentake.dll

客户端启动程序：
D:\0BEIDOUWZ\wingmsmxd\MapleStory.exe

服务端项目：
D:\AI083\AIbeidou\BeiDou-Server-master\gms-server

服务端源码：
D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\src

服务端运行数据：
D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\wz

服务端中文数据：
D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\wz-zh-CN

服务端使用 Java 21、Maven 和 IDEA 启动。

## 二、当前运行时基线

TaleryMS 全量 data 移植已经停止，不能再次将 TaleryMS 全量 Data/wz 覆盖运行目录。

客户端 Data 已恢复自：

D:\0BEIDOUWZ\wingmsmxd\Data-ws

校验结果：Data 为 55,487 个文件，并且与 Data-ws 文件数和总大小一致。

服务端 wz 已恢复自：

D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\备份2wingms\wz-原本

服务端 wz-zh-CN 已恢复自：

D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\备份2wingms\wz-zh-CN

校验结果：

- wz：55,443 个文件
- wz-zh-CN：52 个文件

当前运行目录不要使用名称包含 talery-full-backup、mixed-preserved 或 rollback-preserved 的目录。

## 三、当前客户端 DLL

当前使用的是 Talery 全量 data 移植前、已经启动验证过的版本：

D:\0BEIDOUWZ\wingmsmxd\Kaentake.dll

SHA-256：

880C581CC1389C95D051DA42906AEDEABF2BFF7E9941A46393A44906E23F208D

不要直接替换为 post-talery-data-fix 版本，除非重新完成编译、备份和启动测试。

重要备份：

D:\0BEIDOUWZ\wingmsmxd\Kaentake.dll.bak-before-itemeff-lifetime-20260830-155859.dll
D:\0BEIDOUWZ\wingmsmxd\Kaentake.dll.post-talery-data-fix-backup-20260830-161221.dll

## 四、已知闪退记录

Talery 全量 data 期间曾出现：

故障程序：MapleStory.exe
故障模块：Kaentake.dll
异常代码：0xC0000005
故障偏移：Kaentake.dll+0x1F927

根因定位到 src/itemeff.cpp 的全局 std::map<int, IWzPropertyPtr>。ItemEff.img 数据增大后，DLL 卸载时 COM/WZ 对象释放顺序冲突，造成访问冲突。

源码中已经有生命周期修复版本，但当前运行 DLL 已恢复到稳定的移植前版本。后续不要未经测试直接切换 DLL。

## 五、源码功能状态

客户端源码保留之前已经开发的功能代码，包括：

- 新角色投骰子和 10 级前自由加点
- 聊天表情
- 游戏设置
- 天气系统
- BOSS 血条、BOSS 情报和战斗统计
- 商店回购
- Talery 仓库
- 护肩装备槽相关代码
- 灯泡显示控制
- 多分辨率和其他 Kaentake Hook

注意：当前是“运行时 Data/DLL 回退”，不是“删除全部功能源码”。不要为了回退运行数据而删除这些源码。

## 六、GitHub 备份

客户端仓库：
https://github.com/kuku-996/kaentake-main

客户端当前提交：
929c48eb30eb5dc2f43759af41da0c05ee41c542

客户端备份标签：
https://github.com/kuku-996/kaentake-main/releases/tag/backup-pre-talery-data-20260830

服务端仓库：
https://github.com/kuku-996/BeiDou-Server

服务端备份分支：
https://github.com/kuku-996/BeiDou-Server/tree/backup-pre-talery-data-20260830

服务端备份提交：
04cf316058d712f3319e365f2cf152e754758fef

服务端备份说明：
https://github.com/kuku-996/BeiDou-Server/blob/backup-pre-talery-data-20260830/gms-server/docs/ROLLBACK_PRE_TALERY_DATA_20260830.md

服务端 GitHub 备份基于远程 master 增量保存源码和回退说明，复用了远程已有的 WZ 数据。一个 42 MB 的演示视频资源未上传 GitHub，但仍保留在本机且不参与服务端运行。

## 七、本地保留的回退前数据

客户端 Talery/混合数据保留目录：

D:\0BEIDOUWZ\wingmsmxd\Data-mixed-preserved-20260830-161007
D:\0BEIDOUWZ\wingmsmxd\Data-rollback-preserved-mixed-20260830-160746
D:\0BEIDOUWZ\wingmsmxd\Data-talery-full-backup-20260830-160531

服务端 Talery 数据保留目录：

D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\wz-talery-full-backup-20260830-161029
D:\AI083\AIbeidou\BeiDou-Server-master\gms-server\wz-zh-CN-talery-full-backup-20260830-161029

这些目录只用于恢复或对比，不能作为当前运行目录。

## 八、启动验证

启动服务端：

1. IDEA 打开 gms-server 目录。
2. 确认工作目录不是父目录。
3. 确认加载的是当前 wz 和 wz-zh-CN。
4. 如 IDEA 使用旧 target/classes，先执行 Maven 编译或 IDEA Rebuild。
5. 启动后检查控制台是否有 WZ/XML 错误。

启动客户端：

1. 确认 MapleStory.exe 没有残留进程。
2. 从 wingmsmxd 目录启动 MapleStory.exe。
3. 首先测试登录、选角和初始地图。
4. 再测试聊天、仓库、商城、地图切换。
5. 如闪退，记录准确时间，并检查 Windows 应用程序日志中的 Faulting module、Exception code 和 Faulting offset。

不要只根据“闪退”猜测原因，必须记录新的故障偏移。

## 九、新功能开发规则

- 不要再次复制 TaleryMS 全量 Data 到客户端运行目录。
- 不要再次复制 TaleryMS 全量 wz XML 到服务端运行目录。
- 修改 DLL 前必须备份当前 Kaentake.dll。
- 修改后必须重新编译 Release x86 injector。
- 必须确认新 DLL 的实际输出路径、大小、时间和 SHA-256。
- 先启动测试，再进入地图或打开相关功能。
- 客户端基址和 Hook 必须按 GMS083 验证。
- 服务端运行数据恢复不等于服务端源码回滚。
- 用户只要求分析时，不要覆盖运行文件。
- 用户要求回退时，先区分运行数据、DLL 和源码提交。

Visual Studio CMake：

C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe

客户端构建命令：

~~~powershell
& 'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe' --build 'D:\Codex\从0开始客户端\kaentake-official\build-chat-ui-pch' --config Release --target injector --parallel 4
~~~

## 十、交接结论

当前稳定基线：

- 客户端使用 GMS083 移植前 Data
- 服务端使用 GMS083 移植前 wz/XML
- 客户端使用已验证的移植前 Kaentake.dll
- TaleryMS 全量 data 不再使用
- 客户端和服务端 GitHub 备份均已完成
- 后续所有开发均在此基线上进行

