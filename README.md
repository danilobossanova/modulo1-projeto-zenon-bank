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

## ✅ Tarefa 2 — Modelagem com Records

Modelagem das transações do PaySim com records imutáveis, validados na criação.

### Checklist do enunciado

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/02-records` | ✅ |
| 2 | Record `Transaction` com as colunas do CSV, no pacote `br.com.zenon.fraud` | ✅ ¹ |
| 3 | `camelCase`, tipos corretos, `type` restrito aos cinco valores | ✅ |
| 4 | Abstração: colunas de origem e de destino como tipos próprios | ✅ |
| 5 | `Main` define as duas transações do enunciado e as imprime | ✅ |

¹ As 11 colunas estão todas representadas, mas o record tem 7 componentes: as seis colunas de cliente foram agrupadas em `TransactionCustomer`, atendendo ao requisito 4.

### Estrutura

```
br/com/zenon/fraud/
  Transaction.java           record com 7 componentes
  TransactionCustomer.java   record: nome + saldo antes + saldo depois
  TransactionType.java       enum: CASH_IN, CASH_OUT, DEBIT, PAYMENT, TRANSFER
```

### Mapeamento das colunas

| Colunas do CSV | Componente |
|---|---|
| `step`, `type`, `amount` | `step`, `type`, `amount` |
| `nameOrig`, `oldbalanceOrg`, `newbalanceOrig` | `origin` |
| `nameDest`, `oldbalanceDest`, `newbalanceDest` | `recipient` |
| `isFraud`, `isFlaggedFraud` | `isFraud`, `isFlaggedFraud` |

A tripla *(nome, saldo antes, saldo depois)* aparece duas vezes no CSV; extraí-la em `TransactionCustomer` elimina a duplicação e impede trocar por engano um saldo de origem por um de destino.

### Escolha dos tipos

| Campo | Tipo | Motivo |
|---|---|---|
| `step` | `int` | hora simulada da transação (1 a 744) |
| `type` | `enum TransactionType` | o domínio admite apenas cinco valores |
| `amount` e saldos | `BigDecimal` | valores monetários; `double` acumula erro de ponto flutuante binário |
| `isFraud`, `isFlaggedFraud` | `boolean` | o CSV traz 0/1, mas o domínio é binário |

Os `BigDecimal` são construídos a partir de `String` (`new BigDecimal("9839.64")`). O construtor que recebe `double` carregaria para dentro do valor o ruído da representação binária, anulando o motivo de usar `BigDecimal`.

### Validação

O *compact constructor* rejeita nulos, `step` menor que 1, `amount` negativo e nome em branco.

**Não** é validada a coerência entre saldos e valor (`newBalance == oldBalance - amount`): essa inconsistência ocorre no PaySim real e é parte do sinal usado para detectar fraude. Transformá-la em exceção impediria a leitura do dataset nas tarefas seguintes.

### Execução

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -cp target/classes br.com.zenon.Main
```
