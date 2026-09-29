package com.yenaly.han1meviewer.logic.exception

class ParseException : RuntimeException {

    constructor(
        funcName: String,
        varName: String
    ) : super("[Parse::$funcName => $varName] parse error!")

    constructor(reason: String) : super(reason)
}
