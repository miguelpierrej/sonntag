def process_data(input_data) -> list[dict]:
    if isinstance(input_data[0], str):
        listas_para_processar = [input_data]
    else:
        listas_para_processar = input_data

    final_programs = []
    titulos_secoes = ["TESOROS DE LA BIBLIA", "SEAMOS MEJORES MAESTROS", "NUESTRA VIDA CRISTIANA"]

    for sublista in listas_para_processar:
        itens = [i for i in sublista if i not in ['Configuración de privacidad', 'Guía de actividades...']]

        if len(itens) < 4:
            continue
            
        if not any(char.isdigit() for char in itens[0]):
            continue

        programa = {
            "metadata": {
                "data": itens[0],
                "texto_biblico": itens[1],
                "introducao": itens[2]
            },
            "secoes": [],
            "conclusao": None
        }

        secao_atual = None

        for i in range(3, len(itens)):
            texto = itens[i]

            if "Palabras de conclusión" in texto:
                programa["conclusao"] = texto
                secao_atual = None
            
            elif texto in titulos_secoes:
                secao_atual = {"titulo": texto, "itens": []}
                programa["secoes"].append(secao_atual)
            
            elif secao_atual is not None:
                secao_atual["itens"].append(texto)

        final_programs.append(programa)

    return final_programs