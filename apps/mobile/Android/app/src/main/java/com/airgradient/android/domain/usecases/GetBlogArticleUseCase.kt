package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.Article
import com.airgradient.android.domain.models.BlogPost
import com.airgradient.android.domain.repositories.KnowledgeHubRepository
import javax.inject.Inject

class GetBlogArticleUseCase @Inject constructor(
    private val repository: KnowledgeHubRepository
) {
    suspend operator fun invoke(post: BlogPost): Result<Article> = repository.getArticle(post)
}
