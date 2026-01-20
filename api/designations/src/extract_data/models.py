from sqlmodel import SQLModel, Field
from typing import List, Dict, Optional

class Meetings(SQLModel, table=True):
    __tablename__ = "meetings"

    id: Optional[int] = Field(primary_key=True)

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
    parts_of_third_title: List[Dict]

    conclusion_words: str = Field(...)
