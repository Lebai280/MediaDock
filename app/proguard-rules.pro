# MediaDock — 默认不开启混淆（release 亦为 false）。
# 后续若要发布正式包再按需添加规则，例如 OkHttp / kotlinx.serialization 相关。

# SMBJ 的 SPNEGO/Kerberos 路径引用了 Android 不提供的 Java GSS 类；
# 本项目只用 NTLM 认证（SMB2/3），这些缺失类不影响运行。
-dontwarn org.ietf.jgss.**
-dontwarn javax.security.auth.kerberos.**
-dontwarn javax.security.sasl.**
-dontwarn org.slf4j.**
# SMBJ 的事件总线（mbassy）可选支持 EL 表达式，Android 没有 javax.el。
-dontwarn javax.el.**
