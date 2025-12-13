from fastapi import Body, FastAPI, Path, Query  # type: ignore
from flask import Flask  # type: ignore

# FastAPI example
app = FastAPI()


@app.get("/users")
def get_users():
    return []


@app.get("/users/{user_id}")
def get_user(user_id: int = Path(...)):
    return {"id": user_id}


@app.post("/users")
def create_user(user: dict = Body(...)):
    return user


@app.get("/search")
def search_users(name: str = Query(...), age: int = Query(None)):
    return []


# Flask example
flask_app = Flask(__name__)


@flask_app.route("/api/products", methods=["GET", "POST"])
def products():
    return []


@flask_app.get("/api/products/<int:product_id>")
def get_product(product_id):
    return {"id": product_id}
