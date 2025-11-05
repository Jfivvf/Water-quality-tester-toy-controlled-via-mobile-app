from flask import Flask, request, jsonify
import psycopg2
import bcrypt
import jwt
import datetime
import smtplib
from email.message import EmailMessage
import random

app = Flask(__name__)
app.config['SECRET_KEY'] = '17fbbd797503e97d6f9bba45124a006ca4cda858a82077b7dabfad18474b277d'

EMAIL_ADDRESS = "watertestertoyapp@gmail.com"
EMAIL_PASSWORD = "xrdrxksqrrqqkbhb"

def get_db_connection():
    return psycopg2.connect(
        host="localhost",
        database="water_app_db",
        user="postgres",
        password="Te@le888fon555"
    )

def send_email_utf8(to_email: str, subject: str, body: str):
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = EMAIL_ADDRESS
    msg["To"] = to_email
    msg.set_content(body, charset="utf-8")

    with smtplib.SMTP("smtp.gmail.com", 587) as smtp:
        smtp.starttls()
        smtp.login(EMAIL_ADDRESS, EMAIL_PASSWORD)
        smtp.send_message(msg)


@app.route('/')
def home():
    return "Сервер работает."

@app.route('/users')
def get_users():
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute('SELECT id, username, role FROM users;')
    users = cur.fetchall()
    cur.close()
    conn.close()
    return jsonify(users)

@app.route('/register', methods=['POST'])
def register():
    data = request.get_json(force=True)
    username = data.get('username', '').strip()
    email = data.get('email', '').strip().lower()
    password = data.get('password', '')
    role = data.get('role', '').strip()

    if not username or not email or not password or role not in ('child', 'parent'):
        return jsonify({"error": "Invalid data (username, email, password, role)"}), 400

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT 1 FROM users WHERE username=%s OR email=%s", (username, email))
    if cur.fetchone():
        cur.close()
        conn.close()
        return jsonify({"error": "A user with such username or email already exists"}), 400

    password_hash = bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')

    code = f"{random.randint(0, 999999):06d}"
    expires_at = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=10)

    cur.execute("""
        INSERT INTO email_verifications (email, username, password_hash, role, code, expires_at)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (email) DO UPDATE SET
            username=EXCLUDED.username,
            password_hash=EXCLUDED.password_hash,
            role=EXCLUDED.role,
            code=EXCLUDED.code,
            created_at=NOW(),
            expires_at=EXCLUDED.expires_at
    """, (email, username, password_hash, role, code, expires_at))
    conn.commit()
    cur.close()
    conn.close()

    try:
        send_email_utf8(
            email,
            "Код подтверждения регистрации",
            f"Ваш код подтверждения: {code}\nОн действует 10 минут."
        )
    except Exception as e:
        return jsonify({"error": "Failed to sent the letter", "details": str(e)}), 500

    return jsonify({"message": "the confirmation code has been sent to the email"})

@app.route('/verify', methods=['POST'])
def verify():
    data = request.get_json(force=True)
    email = data.get('email', '').strip().lower()
    code = data.get('code', '').strip()

    if not email or not code or len(code) != 6 or not code.isdigit():
        return jsonify({"error": "Incorrect data (email, code)"}), 400

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""
        SELECT username, password_hash, role, code, expires_at
        FROM email_verifications
        WHERE email=%s
    """, (email,))
    row = cur.fetchone()

    if not row:
        cur.close()
        conn.close()
        return jsonify({"error": "This email has no confirmation request"}), 400

    username, password_hash, role, stored_code, expires_at = row

    if hasattr(expires_at, "tzinfo") and expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=datetime.timezone.utc)

    now_utc = datetime.datetime.now(datetime.timezone.utc)
    if now_utc > expires_at:
        cur.execute("DELETE FROM email_verifications WHERE email=%s", (email,))
        conn.commit()
        cur.close()
        conn.close()
        return jsonify({"error": "Code is expired. Request a new code."}), 400

    if code != stored_code:
        cur.close()
        conn.close()
        return jsonify({"error": "Invalid code"}), 400

    cur.execute("SELECT 1 FROM users WHERE username=%s OR email=%s", (username, email))
    if cur.fetchone():
        cur.execute("DELETE FROM email_verifications WHERE email=%s", (email,))
        conn.commit()
        cur.close()
        conn.close()
        return jsonify({"error": "User already exists"}), 400

    cur.execute("""
        INSERT INTO users (username, email, password, role, is_verified)
        VALUES (%s, %s, %s, %s, TRUE)
        RETURNING id
    """, (username, email, password_hash, role))
    user_id = cur.fetchone()[0]

    cur.execute("DELETE FROM email_verifications WHERE email=%s", (email,))
    conn.commit()
    cur.close()
    conn.close()

    return jsonify({"message": "Registration is completed", "user_id": user_id})

@app.route('/login', methods=['POST'])
def login():
    data = request.get_json(force=True)
    identifier = data.get('identifier', '').strip()
    password = data.get('password', '')

    if not identifier or not password:
        return jsonify({"error": "Enter login/email and password"}), 400

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, username, email, password, is_verified
        FROM users
        WHERE username=%s OR email=%s
    """, (identifier, identifier))
    user = cur.fetchone()
    cur.close()
    conn.close()

    if not user:
        return jsonify({"error": "User is not found"}), 404

    user_id, username, email, password_hash, is_verified = user

    if not is_verified:
        return jsonify({"error": "Email is not confirmed"}), 403

    if not bcrypt.checkpw(password.encode('utf-8'), password_hash.encode('utf-8')):
        return jsonify({"error": "Неверный пароль"}), 401

    token = jwt.encode({
        'user_id': user_id,
        'exp': datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(hours=1)
    }, app.config['SECRET_KEY'], algorithm='HS256')

    return jsonify({"message": "Login successful", "token": token, "user_id": user_id})


@app.route('/measurements', methods=['GET'])
def get_measurements():
    user_id = request.args.get('user_id', type=int)
    if not user_id:
        return jsonify({"error": "user_id is necessary"}), 400

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""
        SELECT id, location, temperature, salinity, measured_at, latitude, longitude, is_danger
        FROM measures
        WHERE user_id = %s
        ORDER BY measured_at DESC
    """, (user_id,))
    rows = cur.fetchall()
    cur.close()
    conn.close()

    measurements = []
    for row in rows:
        mid, loc, temp, sal, created_at, lat, lon, danger = row
        measurements.append({
            "id": mid,
            "location": loc or "",
            "temperature": temp,
            "salinity": sal,
            "created_at": created_at.strftime("%Y-%m-%d %H:%M:%S"),
            "latitude": lat,
            "longitude": lon,
            "is_danger": bool(danger)
        })
    return jsonify(measurements)



@app.route('/measurements', methods=['POST'])
def save_measurement():
    data = request.get_json(force=True)

    user_id = data.get('user_id')
    location = (data.get('location') or '').strip() or 'unknown'
    temperature = data.get('temperature')
    salinity = data.get('salinity')
    latitude = data.get('latitude')
    longitude = data.get('longitude')

    if not isinstance(user_id, int) or temperature is None or salinity is None:
        return jsonify({"error": "Неверные данные (user_id, temperature, salinity)"}), 400

    SAFE_TDS = 650
    is_danger = salinity > SAFE_TDS

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("""
        INSERT INTO measures (user_id, location, temperature, salinity, latitude, longitude, is_danger, measured_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, NOW())
    """, (user_id, location, float(temperature), float(salinity),
          float(latitude) if latitude is not None else None,
          float(longitude) if longitude is not None else None,
          is_danger))
    conn.commit()
    cur.close()
    conn.close()
    return jsonify({"message": "Измерение сохранено"})


@app.route('/get_user_info', methods=['POST'])
def get_user_info():
    data = request.get_json()
    user_id = data.get("user_id")

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT username FROM users WHERE id = %s", (user_id,))
    row = cur.fetchone()
    cur.close()
    conn.close()

    if row:
        return jsonify({"username": row[0]})
    else:
        return jsonify({"error": "User is not found"}), 404

@app.route('/update_username', methods=['POST'])
def update_username():
    data = request.get_json()
    user_id = data.get("user_id")
    new_username = data.get("new_username")

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("UPDATE users SET username = %s WHERE id = %s", (new_username, user_id))
    conn.commit()
    cur.close()
    conn.close()
    return jsonify({"message": "Username was updated"})

@app.route('/update_password', methods=['POST'])
def update_password():
    data = request.get_json()
    user_id = data.get("user_id")
    current_password = data.get("current_password")
    new_password = data.get("new_password")

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("SELECT password FROM users WHERE id = %s", (user_id,))
    row = cur.fetchone()

    if not row:
        cur.close()
        conn.close()
        return jsonify({"error": "User is not found"}), 404

    stored_hashed_password = row[0]

    if not bcrypt.checkpw(current_password.encode('utf-8'), stored_hashed_password.encode('utf-8')):
        cur.close()
        conn.close()
        return jsonify({"error": "Invalid current password"}), 401

    new_hashed = bcrypt.hashpw(new_password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("UPDATE users SET password = %s WHERE id = %s", (new_hashed, user_id))
    conn.commit()
    cur.close()
    conn.close()
    return jsonify({"message": "The password was updated"})

import base64

@app.route('/update_avatar', methods=['POST'])
def update_avatar():
    data = request.get_json()
    user_id = data.get("user_id")
    avatar_b64 = data.get("avatar")

    if not avatar_b64:
        return jsonify({"error": "No picture"}), 400

    conn = get_db_connection()
    cur = conn.cursor()
    cur.execute("UPDATE users SET avatar = %s WHERE id = %s", (avatar_b64, user_id))
    conn.commit()
    cur.close()
    conn.close()
    return jsonify({"message": "Avatar was updated"})




if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)


