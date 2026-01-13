class DateTimeConfigError(Exception):
    def __init__(self, error_message):
        self.detail = "Houve um erro ao preparar as datas para extração"
        self.error_message = error_message
        
        super().__init__(error_message)

    def __str__(self):
        return f"{self.error_message} -> Detalhe: {self.detail}"