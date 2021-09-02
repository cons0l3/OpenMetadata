# generated by datamodel-codegen:
#   filename:  schema/api/tags/createTagCategory.json
#   timestamp: 2021-09-02T03:08:31+00:00

from __future__ import annotations

from pydantic import BaseModel, Field

from ...entity.tags import tagCategory


class CreateTagCategoryRequest(BaseModel):
    name: tagCategory.TagName
    description: str = Field(..., description='Description of the tag category')
    categoryType: tagCategory.TagCategoryType
