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

## ✅ Tarefa 8 — Internacionalização do relatório

O relatório de fraudes exportável em português ou inglês: texto, moeda e formato dos números conforme o idioma escolhido pelo usuário.

### O que o enunciado pediu

| # | Requisito | Status |
|---|---|---|
| 1 | Branch `tarefa/08-i18n`, com commit e push da entrega | 🚧 ¹ |
| 2 | `ReportMain` internacionalizada para português e inglês | ✅ |
| 2.1 | Bundles `report_pt.properties` e `report_en.properties`, com `ResourceBundle` para os cabeçalhos | ✅ |
| 2.2 | Valores monetários formatados com `NumberFormat` conforme o `Locale` | ✅ |
| 2.3 | Argumento em que o usuário escolhe entre português e inglês | ✅ |

¹ Branch criada e commits da entrega feitos. O push é o passo que fecha a tarefa.

### Resultado

```
java -Xmx128m -cp target/classes br.com.zenon.ReportMain pt

Total de linhas: 6.362.620
Total de fraudes: 8.213
Valor total transacionado: R$ 1.144.392.944.759,77
```

```
java -Xmx128m -cp target/classes br.com.zenon.ReportMain en

Total lines: 6,362,620
Total frauds: 8,213
Total transacted value: $1,144,392,944,759.77
```

Sem argumento, o relatório sai em português.

Vale comparar as duas saídas com atenção. Além do texto traduzido, mudaram o separador de milhar (`.` contra `,`), o separador decimal (`,` contra `.`), o símbolo monetário e até a posição dele: `R$ 1.144…` com espaço antes do número, `$1,144…` sem. Nenhuma dessas regras está no código — todas vêm do `Locale`.

### Estrutura

| Arquivo | Papel |
|---|---|
| `src/main/resources/report_pt.properties` | cabeçalhos em português |
| `src/main/resources/report_en.properties` | cabeçalhos em inglês |
| `ReportMain` | resolve o `Locale`, carrega o bundle e formata os números |

### Decisões de implementação

**Idioma e país são eixos independentes.** Os bundles são nomeados apenas pelo idioma (`report_pt`), mas os `Locale` do código trazem o país (`pt-BR`, `en-US`). Não é inconsistência, é a separação correta: traduzir texto é questão de **idioma**, enquanto moeda e separadores são questão de **país**. Existe português de Portugal com euro e português do Brasil com real, com o mesmo texto traduzido.

E o país não é dispensável aqui: `NumberFormat.getCurrencyInstance` para um locale só de idioma não tem como saber qual é a moeda, e cai no símbolo genérico em vez de `R$` ou `$`. A cadeia de resolução do `ResourceBundle` liga as duas pontas sozinha — para `pt-BR` ele procura `report_pt_BR`, não encontra, e aceita `report_pt`.

**O argumento é validado antes de chegar ao `ResourceBundle`.** Diante de um idioma sem bundle, `getBundle` **não** falha: ele recorre ao locale padrão da JVM. Em uma máquina configurada em português, pedir `fr` imprimiria o relatório em português como se estivesse tudo certo — o pior tipo de defeito, o que não se anuncia. Validar antes troca o erro silencioso por uma mensagem explícita:

```
java -cp target/classes br.com.zenon.ReportMain fr

java.lang.IllegalArgumentException: idioma não suportado: fr — use pt, en
```

**`.properties` em UTF-8, sem escapes.** Desde o Java 9 o `ResourceBundle` lê `.properties` como UTF-8, com recuo automático para ISO-8859-1. Em Java 8 os acentos precisariam ser escritos como `\uXXXX`, e um arquivo UTF-8 apareceria corrompido na saída.

**`toLowerCase(Locale.ROOT)` na leitura do argumento.** Sem o locale explícito, o método usa o padrão da JVM. Em turco a minúscula de I é `ı`, sem ponto: com um locale `tr-TR`, `"IT".toLowerCase()` devolve `ıt`, que não casa com `it`. Com `pt` e `en` o problema não se manifesta, porque nenhum dos dois contém a letra I — a proteção é preventiva, para o dia em que a lista receber um `it` ou um `id`.

**Os contadores também passam pelo `NumberFormat`.** O enunciado pede a formatação dos valores monetários; aplicá-la também a `totalLines` e `totalFrauds` é o que faz `6362620` virar `6.362.620` em português e `6,362,620` em inglês. Isso altera a saída em relação à tarefa 7, onde os contadores saíam sem separador de milhar.

**Uma ressalva sobre a moeda.** Mostrar o mesmo total como `R$ 1.144.392.944.759,77` e `$1,144,392,944,759.77` é o que o enunciado pede, e está correto como exercício de formatação. Mas afirma que os dois valores são equivalentes, e eles não são: não há conversão de câmbio, e o PaySim não declara moeda alguma. Em um produto real o valor viajaria junto com a sua moeda de origem, e a localização formataria sem reinterpretar quanto ele vale.

**Cabeçalhos no bundle, não frases inteiras.** O enunciado pede o bundle "para cabeçalhos", e é isso que está lá: o código monta `cabeçalho + ": " + valor`. Para um produto, a frase inteira com um `{0}` e `MessageFormat` seria melhor, porque pontuação e ordem das palavras variam por idioma — em francês, por exemplo, usa-se espaço antes dos dois-pontos.

### Como foi verificado

| Cenário | Resultado |
|---|---|
| `pt` e sem argumento | texto e formato em português do Brasil |
| `en` | texto e formato em inglês dos Estados Unidos |
| `EN`, em maiúscula | aceito |
| `fr` | `IllegalArgumentException` com a lista de idiomas atendidos |
| locale padrão `tr-TR` com argumento `EN` | funciona |

A última linha merece uma ressalva honesta: ela **não** prova nada sobre o bug do I turco, porque `EN` não contém a letra I. Para verificar a afirmação testei o comportamento isoladamente — em um locale `tr-TR`, `"IT"` e `"ID"` viram `ıt` e `ıd` e deixam de casar com `it` e `id`, enquanto `EN` e `PT` saem idênticos nos dois locales.

### Execução

#### No IntelliJ

`Run → Edit Configurations → + → Application`:

| Campo | Valor |
|---|---|
| Main class | `br.com.zenon.ReportMain` |
| Program arguments | `pt` ou `en` |
| VM options | `-Xmx128m` |
| Working directory | a pasta `zenon-fraud-detector` |

O **working directory** importa: os caminhos dos CSVs são relativos a ele. Com a raiz do repositório selecionada, a leitura falha com `NoSuchFileException`.

Vale marcar *Store as project file*, para que a configuração fique em `.run/` e seja versionada. O padrão do IntelliJ é gravar em `.idea/`, que este repositório ignora (`.gitignore`, linha 35): ali ela existiria apenas na máquina de quem a criou.

#### Pelo terminal

```bash
cd zenon-fraud-detector
mvn -q clean compile
java -Xmx128m -cp target/classes br.com.zenon.ReportMain en
```

Os `.properties` precisam estar em `src/main/resources` para que o Maven os copie para `target/classes`; fora dali o `ResourceBundle` não os encontra.

Os CSVs ficam em `data/` e não são versionados.

O projeto exige **JDK 25**: as classes de entrada usam o método de instância `void main()` e `IO.println`, finalizados nessa versão.
