package com.voiceink.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 自定义分类体系（AI 学习用户习惯的载体，§4.1） */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val kind: String,                       // "theme" | "type"
    val usageCount: Int = 0,                // 历史使用频次，注入 Prompt 供 AI 参考
    val userCreated: Boolean = false
)
