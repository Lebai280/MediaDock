package com.lovelev.mediadock.ui.nav

/** 起始页只看本地是否已有启用的数据源；不在这里发网络请求。 */
internal fun initialRoute(hasEnabledSource: Boolean): String =
    if (hasEnabledSource) Routes.LIBRARY else Routes.ADD_SOURCE
