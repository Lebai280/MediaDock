package com.lovelev.mediadock.data.prefs

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lovelev.mediadock.data.subtitle.OpenSubtitlesCredentials
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64

private val Context.credentialDataStore: DataStore<Preferences> by preferencesDataStore("credentials")

/** 已保存的 WebDAV 数据源。 */
data class StoredSource(
    val url: String,
    val username: String,
    val password: String,
    val rootPath: String,
)

/**
 * 数据源凭据存储。
 *
 * 地址/用户名/根路径是明文无所谓，**密码用 Android Keystore 里的 AES-GCM 密钥加密后
 * 再落盘**，密钥本身不出安全硬件、不落盘。
 *
 * **解密失败 = 凭据损坏，当作没有数据源。** 典型场景是恢复备份 / 换机器 / 重装：
 * DataStore 里的密文还在，但 Keystore 密钥不在了。此时如果退化成“空密码 + 已登录”，
 * 扫描和播放会拿空密码去发 Basic 认证 —— 那正是会把所有人锁在门外的行为。
 */
class SecureCredentialStore(private val context: Context) {

    private val keyUrl = stringPreferencesKey("source_url")
    private val keyUser = stringPreferencesKey("source_user")
    private val keyPassEnc = stringPreferencesKey("source_pass_enc")
    private val keyRoot = stringPreferencesKey("source_root")

    /** TMDB 凭据（API Read Access Token 或 32 位 api_key）。同样加密存，**绝不进代码/BuildConfig**。 */
    private val keyTmdb = stringPreferencesKey("tmdb_secret_enc")

    /** 字幕源（OpenSubtitles）凭据。 */
    private val keySubKey = stringPreferencesKey("subtitle_api_key_enc")
    private val keySubUser = stringPreferencesKey("subtitle_user")
    private val keySubPass = stringPreferencesKey("subtitle_pass_enc")

    /**
     * 本地媒体目录（SAF 树 URI）。**不是机密**，放在这里只为省一个 DataStore 实例 ——
     * 它是「本地」这个数据源的配置，和 WebDAV 那套平行。
     */
    private val keyLocalTree = stringPreferencesKey("local_tree_uri")

    /**
     * 主题三态（跟随系统 / 浅色 / 深色）。**不是机密**，放这里只为省一个 DataStore；
     * 和本地目录一样，`clearSource()` 不会碰它 —— 退出登录不该把主题重置掉。
     */
    private val keyThemeMode = stringPreferencesKey("theme_mode")

    /** 供界面立即拿到当前值（DataStore 是异步的，首帧不该闪一下）。 */
    @Volatile
    var cachedThemeMode: String = "system"
        private set

    val themeMode: Flow<String> = context.credentialDataStore.data.map { prefs ->
        (prefs[keyThemeMode] ?: "system").also { cachedThemeMode = it }
    }

    suspend fun saveThemeMode(mode: String) {
        cachedThemeMode = mode
        context.credentialDataStore.edit { it[keyThemeMode] = mode }
    }

    suspend fun loadLocalTree(): String? =
        context.credentialDataStore.data.first()[keyLocalTree]?.takeIf { it.isNotBlank() }

    suspend fun saveLocalTree(uri: String) {
        context.credentialDataStore.edit { it[keyLocalTree] = uri.trim() }
    }

    /** 只清本地媒体目录 —— 与「退出登录」语义不同，不得同时清除 WebDAV 凭据。 */
    suspend fun clearLocalTree() {
        context.credentialDataStore.edit { it.remove(keyLocalTree) }
    }

    /**
     * 最近一次读到 / 写入的凭据，供**不能挂起**的地方同步取用 ——
     * 特别是 OkHttpDataSource 在发媒体请求时补 Authorization 头。
     */
    @Volatile
    var cached: StoredSource? = null
        private set

    suspend fun load(): StoredSource? {
        val p = context.credentialDataStore.data.first()
        val url = p[keyUrl]
        val user = p[keyUser]
        if (url == null || user == null) {
            cached = null
            return null
        }
        // 密文不存在或解不开 → 凭据已损坏（Keystore 密钥被销毁/换了设备）。
        // 这里必须返回 null，让调用方回到「添加数据源」；绝不允许带着空密码继续请求。
        val encrypted = p[keyPassEnc]
        val password = if (encrypted == null) null else CryptoBox.decryptOrNull(encrypted)
        if (password == null) {
            cached = null
            return null
        }
        val source = StoredSource(
            url = url,
            username = user,
            password = password,
            rootPath = p[keyRoot].orEmpty(),
        )
        cached = source
        return source
    }

    suspend fun save(source: StoredSource) {
        context.credentialDataStore.edit { p ->
            p[keyUrl] = source.url
            p[keyUser] = source.username
            p[keyPassEnc] = CryptoBox.encrypt(source.password)
            p[keyRoot] = source.rootPath
        }
        cached = source
    }

    /**
     * 退出登录：**只删数据源那几项**（地址 / 用户名 / 密码 / 根路径）。
     *
     * 刻意**不** `clear()` 整个 DataStore —— 那会把 TMDB 凭据一起删掉，而设置页的
     * 确认文案只说「清除数据源地址、账号和密码」、还说「媒体库与播放进度会留在本机」，
     * 用户不会知道为什么退出登录之后刮削又失效了、还得回去重新粘 key。
     * （改这里请同步看 SettingsScreen 里 `Confirmable.Logout` 的文案。）
     */
    suspend fun clearSource() {
        context.credentialDataStore.edit { p ->
            p.remove(keyUrl)
            p.remove(keyUser)
            p.remove(keyPassEnc)
            p.remove(keyRoot)
        }
        cached = null
    }

    suspend fun loadTmdbSecret(): String? =
        context.credentialDataStore.data.first()[keyTmdb]?.let(CryptoBox::decrypt)?.takeIf { it.isNotBlank() }

    suspend fun saveTmdbSecret(secret: String) {
        context.credentialDataStore.edit { it[keyTmdb] = CryptoBox.encrypt(secret.trim()) }
    }

    /**
     * 字幕源（OpenSubtitles）的凭据。Api-Key 和密码都当机密加解密，用户名是明文（和 WebDAV 那套一致）。
     * 没配齐返回 null —— 界面据此引导用户去填。
     */
    suspend fun loadSubtitleCredentials(): OpenSubtitlesCredentials? {
        val p = context.credentialDataStore.data.first()
        val apiKey = p[keySubKey]?.let(CryptoBox::decrypt)?.takeIf { it.isNotBlank() } ?: return null
        val username = p[keySubUser]?.takeIf { it.isNotBlank() } ?: return null
        val password = p[keySubPass]?.let(CryptoBox::decrypt)?.takeIf { it.isNotBlank() } ?: return null
        return OpenSubtitlesCredentials(apiKey = apiKey, username = username, password = password)
    }

    suspend fun saveSubtitleCredentials(credentials: OpenSubtitlesCredentials) {
        context.credentialDataStore.edit { p ->
            p[keySubKey] = CryptoBox.encrypt(credentials.apiKey.trim())
            p[keySubUser] = credentials.username.trim()
            p[keySubPass] = CryptoBox.encrypt(credentials.password)
        }
    }
}

/** Keystore 支持的 AES-GCM 加解密。 */
internal object CryptoBox {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "mediadock.credentials"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 不要求生物识别：播放器要能在后台无人值守时取用凭据
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    /** 返回 base64(iv ‖ ciphertext)。 */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + cipherText
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    /** 解密失败（例如换了设备/清了密钥）返回 null —— 调用方据此区分「解不开」和「本来就是空串」。 */
    fun decryptOrNull(encoded: String): String? = runCatching {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.size <= IV_BYTES) return@runCatching null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(TAG_BITS, packed, 0, IV_BYTES),
        )
        String(cipher.doFinal(packed, IV_BYTES, packed.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    /** 兼容旧调用：解不开时返回空串。**凭据类调用请用 [decryptOrNull]**。 */
    fun decrypt(encoded: String): String = decryptOrNull(encoded).orEmpty()
}
