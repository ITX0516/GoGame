package com.example.gogame.engine.gtp

data class GtpCommand(
    val id: Int? = null,
    val command: String,
    val arguments: List<String> = emptyList()
) {
    fun toProtocolString(): String {
        val sb = StringBuilder()
        if (id != null) {
            sb.append(id)
        }
        sb.append(" ")
        sb.append(command)
        if (arguments.isNotEmpty()) {
            sb.append(" ")
            sb.append(arguments.joinToString(" "))
        }
        return sb.toString().trim()
    }

    companion object {
        fun parse(input: String): GtpCommand {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                return GtpCommand(command = "")
            }

            var id: Int? = null
            var rest = trimmed

            val firstChar = trimmed.firstOrNull()
            if (firstChar?.isDigit() == true) {
                val idStr = trimmed.takeWhile { it.isDigit() }
                id = idStr.toIntOrNull()
                rest = trimmed.substring(idStr.length).trim()
            }

            val parts = rest.split(Regex("\\s+"))
            val command = parts.firstOrNull() ?: ""
            val arguments = parts.drop(1)

            return GtpCommand(id = id, command = command.lowercase(), arguments = arguments)
        }
    }
}

data class GtpResponse(
    val id: Int? = null,
    val isSuccess: Boolean,
    val content: String = ""
) {
    fun toProtocolString(): String {
        val sb = StringBuilder()
        sb.append(if (isSuccess) "=" else "?")
        if (id != null) {
            sb.append(id)
        }
        if (content.isNotEmpty()) {
            sb.append(" ")
            sb.append(content)
        }
        sb.append("\n\n")
        return sb.toString()
    }

    companion object {
        fun parse(input: String): GtpResponse {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) {
                return GtpResponse(isSuccess = false, content = "empty response")
            }

            val isSuccess = trimmed.first() == '='
            var rest = trimmed.substring(1).trimStart()

            var id: Int? = null
            if (rest.firstOrNull()?.isDigit() == true) {
                val idStr = rest.takeWhile { it.isDigit() }
                id = idStr.toIntOrNull()
                rest = rest.substring(idStr.length).trim()
            }

            return GtpResponse(id = id, isSuccess = isSuccess, content = rest)
        }
    }
}
