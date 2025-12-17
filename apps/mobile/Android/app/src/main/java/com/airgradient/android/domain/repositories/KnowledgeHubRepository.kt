package com.airgradient.android.domain.repositories

import com.airgradient.android.domain.models.Article
import com.airgradient.android.domain.models.BlogPost

interface KnowledgeHubRepository {
    suspend fun getLatestPosts(): Result<List<BlogPost>>
    suspend fun getArticle(post: BlogPost): Result<Article>
}
