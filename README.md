# Muppet-Print

Muppet-Print是一个打印内容渲染、打印中转Web服务。提供Windows、MacOS、Linux环境的打包。Muppet Print能将Web API收到的HTML+CSS+JS内容渲染并静默推送到本地打印机。

## 本地Web服务

Muppet-Print多以本地服务或局域网服务的形式运行，例如Windows端的MuppetPrint.exe，启动58080端口的Web服务。

Web网站-->跨域MuppetPrint.exe服务-->局域网打印机，相比在浏览器上执行window.print()，可以实现静默打印。

适用于例如快递、仓库自动接单打印，服务大厅办公电脑共享打印机打印，手机或手持设备APP控制局域网内打印机打印。
