# 🏦 Zenón Bank - Projeto Prático: Fundamentos Java

Bem-vindo ao repositório base do projeto prático da disciplina de Fundamentos Java. Este projeto simula o backend de uma fintech fictícia, o **Zenón Bank**, onde você aplicará conceitos desde manipulação de tipos básicos até processamento de alta performance e concorrência.

## 📋 Sobre o Projeto

O objetivo é consolidar os conhecimentos adquiridos em aula através de um cenário realista. Você lidará com:
* Modelagem de dados com **Records**.
* API de Collections e Streams.
* Manipulação de I/O de arquivos grandes.
* Tratamento de Exceções.
* Integração com Banco de Dados.
* Concorrência e Paralelismo.

Siga o passo a passo do quadro de atividades (Trello) do projeto prático dessa disciplina, disponível nos vídeos de projeto prático da disciplina.

## 📦 Requisitos

* **Git e GitHub**: para controle de versões e portifólio de repositórios.
* **Java JDK**: 25 (LTS).
* **IDE**: IntelliJ IDEA ou Eclipse.
* **Docker e Docker Compose**: Para a tarefa de Banco de Dados.

---

## ✅ Tarefa 3 — Ingestão de dados

Leitura do CSV do PaySim com parsing manual, convertendo cada linha em um record `Transaction`.

### Checklist do enunciado

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/03-ingestao-dados` | ✅ |
| 2 | `TransactionIngestor` recebe o nome do arquivo e retorna `List<Transaction>` | ✅ ¹ |
| 3 | Ler apenas as primeiras 1.000 linhas do CSV | ✅ ² |
| 4 | Parsing manual, instanciar o record `Transaction` e guardar em `List` | ✅ |
| 5 | `Main` imprime as 10 primeiras transações no formato indicado | ✅ ³ |

¹ Declarado como **interface**, não como classe — o contrato (nome do arquivo → `List<Transaction>`) é o do enunciado. Ver "Além do enunciado".
² 1.000 **transações**, correspondendo às linhas 2 a 1001; o cabeçalho não conta como dado.
³ Saída conferida caractere a caractere contra o exemplo do enunciado.

### Estrutura

```
br/com/zenon/fraud/ingestor/
  TransactionIngestor.java       interface
  TransactionLineParser.java     mapeamento das 11 colunas (package-private)
  IoTransactionIngestor.java     java.io   — FileInputStream + Scanner
  NioTransactionIngestor.java    java.nio  — FileChannel + ByteBuffer
  Nio2TransactionIngestor.java   NIO.2     — Files + Path
```

### Além do enunciado

O exercício pedia apenas `java.io`. Foram implementadas as três gerações de API de arquivos do Java, para comparação:

| API | Desde | Abstração central |
|---|---|---|
| `java.io` | Java 1.0 | streams bloqueantes |
| `java.nio` | Java 1.4 | canais e buffers |
| NIO.2 (`java.nio.file`) | Java 7 | `Path` + `Files` |

As três devolvem resultados idênticos e falham com a mesma mensagem; trocar de implementação é alterar uma linha no `Main`.

Decisões relevantes:

* **Parser compartilhado** — o mapeamento das colunas existe em um só lugar, não replicado nas três implementações.
* **Erros identificam a linha** — número e conteúdo da linha na mensagem, e contagem de colunas validada antes do acesso.
* **`java.nio` procura o byte `'\n'`**, não o texto decodificado: um bloco do canal pode cortar um caractere UTF-8 multibyte ao meio.

### Execução

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -cp target/classes br.com.zenon.Main
```

O CSV fica em `data/` e não é versionado.
