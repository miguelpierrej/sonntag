from pydantic import BaseModel, Field
from typing import Dict, List


class Data(BaseModel):
    week: str = Field(...)
    bible_chapter: str = Field(...)
    music_and_introduction_words: str = Field(...)

    first_title: str = Field(...)
    first_part_title: str = Field(...)
    second_part_title: str = Field(...)
    bible_lecture: str = Field(...)

    second_title: str = Field(...)
    parts_of_second_title: List[Dict]

    third_title: str = Field(...)
    parts_of_third_title: str = Field(...)

    conclusion_word: str = Field(...)