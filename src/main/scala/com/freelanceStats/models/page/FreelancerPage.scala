package com.freelanceStats.models.page

import com.freelanceStats.models.page.Page.PageContents
import com.freelanceStats.models.pageMetadata.FreelancerProgressMetadata

case class FreelancerPage(
    metadata: FreelancerProgressMetadata,
    contents: PageContents
) extends Page[FreelancerProgressMetadata]
