package com.freelanceStats.models.page

import com.freelanceStats.models.page.Page.PageContents
import com.freelanceStats.models.pageMetadata.FreelancerPageMetadata

case class FreelancerPage(
    metadata: FreelancerPageMetadata,
    contents: PageContents
) extends Page[FreelancerPageMetadata]
