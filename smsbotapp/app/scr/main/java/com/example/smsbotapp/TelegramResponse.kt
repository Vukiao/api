package com.example.smsbotapp

data class TelegramResponse(val ok: Boolean, val result: List<Update>)
data class Update(val update_id: Long, val message: Message?)
data class Message(val chat: Chat, val text: String?)
data class Chat(val id: Long)