import sqlite3

def get_db_connection():
    """Establishes a connection to the SQLite database."""
    conn = sqlite3.connect('escala.db')
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    """Initializes the database and creates the 'tarefas' table if it doesn't exist."""
    conn = get_db_connection()
    cursor = conn.cursor()
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS tarefas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            semana TEXT NOT NULL,
            tipo TEXT NOT NULL,
            titulo TEXT NOT NULL,
            tempo TEXT,
            designacao TEXT
        )
    ''')
    # Create an index to prevent duplicate tasks for the same week
    cursor.execute('''
        CREATE UNIQUE INDEX IF NOT EXISTS idx_tarefa_unica
        ON tarefas (semana, tipo, titulo)
    ''')
    conn.commit()
    conn.close()
    print("Banco de dados inicializado.")

def salvar_tarefa(tarefa_dict):
    """Saves a single task into the database, ignoring duplicates."""
    conn = get_db_connection()
    cursor = conn.cursor()
    try:
        cursor.execute(
            '''
            INSERT INTO tarefas (semana, tipo, titulo, tempo, designacao)
            VALUES (:semana, :tipo, :titulo, :tempo, :designacao)
            ''',
            tarefa_dict
        )
        conn.commit()
    except sqlite3.IntegrityError:
        # This will happen if the UNIQUE index detects a duplicate. We can safely ignore it.
        # print(f"Tarefa duplicada ignorada: {tarefa_dict['titulo']}")
        pass
    finally:
        conn.close()

def listar_tarefas(semana=None):
    """Lists all tasks, optionally filtered by week."""
    conn = get_db_connection()
    cursor = conn.cursor()
    if semana:
        cursor.execute("SELECT * FROM tarefas WHERE semana = ? ORDER BY id", (semana,))
    else:
        # If no week is specified, return tasks for the most recently inserted week
        cursor.execute("SELECT semana FROM tarefas ORDER BY id DESC LIMIT 1")
        last_week = cursor.fetchone()
        if last_week:
            cursor.execute("SELECT * FROM tarefas WHERE semana = ? ORDER BY id", (last_week['semana'],))
        else:
            conn.close()
            return []  # Return an empty list if the database is empty
            
    tarefas = [dict(row) for row in cursor.fetchall()]
    conn.close()
    return tarefas

def get_semanas():
    """Returns a list of all distinct weeks from the database, most recent first."""
    conn = get_db_connection()
    cursor = conn.cursor()
    # We select weeks based on the latest entries first
    cursor.execute("SELECT semana FROM tarefas GROUP BY semana ORDER BY MAX(id) DESC")
    semanas = [row['semana'] for row in cursor.fetchall()]
    conn.close()
    return semanas
