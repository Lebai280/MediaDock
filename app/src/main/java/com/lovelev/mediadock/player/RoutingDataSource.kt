package com.lovelev.mediadock.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/** 按 scheme 把请求分给 SMB 或默认（file/content/http）数据源。 */
class RoutingDataSource(
    private val smbFactory: DataSource.Factory,
    private val otherFactory: DataSource.Factory,
) : DataSource {

    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun open(dataSpec: DataSpec): Long {
        val factory = if (dataSpec.uri.scheme.equals("smb", ignoreCase = true)) {
            smbFactory
        } else {
            otherFactory
        }
        val source = factory.createDataSource()
        listeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun close() {
        delegate?.close()
        delegate = null
    }

    class Factory(
        private val smbFactory: DataSource.Factory,
        private val otherFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(smbFactory, otherFactory)
    }
}
