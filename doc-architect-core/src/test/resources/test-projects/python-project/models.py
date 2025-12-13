from django.db import models  # type: ignore
from sqlalchemy import Column, Integer, String  # type: ignore
from sqlalchemy.orm import Mapped, mapped_column, relationship  # type: ignore


# SQLAlchemy 1.x style
class UserLegacy:
    __tablename__ = "users_legacy"

    id = Column(Integer, primary_key=True)
    name = Column(String(100), nullable=False)
    email = Column(String(255), unique=True)
    posts = relationship("Post", back_populates="author")


# SQLAlchemy 2.0+ style
class UserModern:
    __tablename__ = "users_modern"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(unique=True)


# Django ORM
class Article(models.Model):
    title = models.CharField(max_length=200)
    content = models.TextField()
    published_at = models.DateTimeField(auto_now_add=True)
    author = models.ForeignKey("User", on_delete=models.CASCADE)
    tags = models.ManyToManyField("Tag", related_name="articles")
