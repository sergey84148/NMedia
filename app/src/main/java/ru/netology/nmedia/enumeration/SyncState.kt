package ru.netology.nmedia.enumeration

enum class SyncState {
    SYNCED,      // Синхронизировано с сервером
    PENDING,     // Ожидает синхронизации (новые/измененные)
    PENDING_DELETE, // Ожидает удаления
    SYNCING,     // В процессе синхронизации
    FAILED       // Ошибка синхронизации
}