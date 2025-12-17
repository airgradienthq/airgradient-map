package com.airgradient.android.domain.usecases

import com.airgradient.android.domain.models.BlogPost
import com.airgradient.android.domain.repositories.KnowledgeHubRepository
import javax.inject.Inject

class GetLatestBlogPostsUseCase @Inject constructor(
    private val repository: KnowledgeHubRepository
) {
    suspend operator fun invoke(): Result<List<BlogPost>> = repository.getLatestPosts()
}
