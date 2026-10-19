package br.com.zenon;

import br.com.zenon.fraud.report.TransactionReport;
import br.com.zenon.fraud.report.TransactionSummary;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Ponto de entrada da Tarefa 8: processa o arquivo completo do PaySim em stream e imprime
 * os totais no idioma escolhido pelo usuário.
 *
 * <p>É uma classe de entrada separada de {@link Main} de propósito. A {@code Main} carrega
 * transações em uma lista para analisá-las e consultá-las, o que a obriga a trabalhar sobre
 * uma fatia do arquivo. Este relatório atravessa os 493 MB inteiros sem guardar nada, e por
 * isso roda com {@code -Xmx128m}.
 *
 * <p>Execução:
 *
 * <pre>{@code
 * java -Xmx128m -cp target/classes br.com.zenon.ReportMain pt
 * java -Xmx128m -cp target/classes br.com.zenon.ReportMain en
 * }</pre>
 *
 * <p>A internacionalização separa duas coisas que costumam ser confundidas. O <em>texto</em>
 * depende do idioma e vem de um {@link ResourceBundle}; a <em>forma dos números</em> — símbolo
 * monetário, separador de milhar, separador decimal — depende do país e vem de um
 * {@link NumberFormat} construído a partir do {@link Locale}. São eixos independentes: existe
 * português de Portugal com euro e português do Brasil com real, com o mesmo texto traduzido.
 */
public class ReportMain {

    /** CSV completo do PaySim: 6.362.620 transações, cerca de 493 MB. */
    private static final String ARQUIVO = "data/PS_20174392719_1491204439457_log.csv";

    /** Nome base dos bundles, resolvido para {@code report_pt} ou {@code report_en}. */
    private static final String BUNDLE = "report";

    /**
     * Idiomas atendidos, em ordem de preferência.
     *
     * <p>Os locales trazem o país ({@code pt-BR}, {@code en-US}) porque sem ele não há moeda
     * definida: {@code NumberFormat.getCurrencyInstance} para um locale apenas de idioma cai
     * no símbolo genérico de moeda em vez de {@code R$} ou {@code $}. Já os bundles são
     * nomeados só pelo idioma, e é a cadeia de resolução do {@code ResourceBundle} que liga
     * os dois: para {@code pt-BR} ele procura {@code report_pt_BR}, não encontra, e aceita
     * {@code report_pt}.
     */
    private static final List<Locale> IDIOMAS = List.of(
            Locale.of("pt", "BR"),
            Locale.of("en", "US"));

    /** Idioma usado quando nenhum argumento é informado. */
    private static final Locale IDIOMA_PADRAO = IDIOMAS.getFirst();

    void main(String[] args) {
        Locale idioma = resolverIdioma(args);

        ResourceBundle textos = carregarTextos(idioma);
        NumberFormat moeda = NumberFormat.getCurrencyInstance(idioma);
        NumberFormat inteiro = NumberFormat.getIntegerInstance(idioma);

        TransactionSummary resumo = new TransactionReport().summarize(ARQUIVO);

        IO.println(textos.getString("report.totalLines") + ": " + inteiro.format(resumo.totalLines()));
        IO.println(textos.getString("report.totalFrauds") + ": " + inteiro.format(resumo.totalFrauds()));
        IO.println(textos.getString("report.totalAmount") + ": " + moeda.format(resumo.totalAmount()));
    }

    /**
     * Converte o argumento de linha de comando no {@link Locale} correspondente.
     *
     * <p>O argumento é validado contra a lista de idiomas atendidos em vez de ser repassado
     * ao {@code ResourceBundle}. O motivo é o comportamento de recuo da própria API: diante de
     * um idioma sem bundle, {@code getBundle} não falha — ele recorre ao locale <em>padrão da
     * JVM</em>. Em uma máquina configurada em português, pedir {@code fr} imprimiria o
     * relatório em português como se nada estivesse errado. Validar antes transforma um erro
     * silencioso em uma mensagem explícita.
     *
     * <p>O {@code toLowerCase} recebe {@link Locale#ROOT} de propósito. Sem argumento ele usa
     * o locale padrão da JVM, e em turco a minúscula de I é {@code ı}, sem ponto: com um locale
     * {@code tr-TR}, {@code "IT".toLowerCase()} devolve {@code "ıt"}, que não casa com
     * {@code "it"}. É o clássico bug do I turco.
     *
     * <p>Com os idiomas atendidos hoje o problema não se manifesta, porque nem {@code pt} nem
     * {@code en} contêm a letra I. Fixar o locale aqui evita que ele apareça no dia em que a
     * lista receber um {@code it} ou um {@code id} — uma falha que dependeria da configuração
     * da máquina de quem executa e seria difícil de associar à troca de caixa.
     *
     * @param args os argumentos recebidos; apenas o primeiro é considerado
     * @return o locale pedido, ou {@link #IDIOMA_PADRAO} se nenhum argumento vier
     * @throws IllegalArgumentException se o idioma pedido não for atendido
     */
    private Locale resolverIdioma(String[] args) {
        if (args.length == 0) {
            return IDIOMA_PADRAO;
        }

        String pedido = args[0].toLowerCase(Locale.ROOT);

        return IDIOMAS.stream()
                .filter(locale -> locale.getLanguage().equals(pedido))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "idioma não suportado: " + args[0] + " — use " + idiomasAtendidos()));
    }

    /**
     * Carrega os textos do idioma, traduzindo a falha em uma mensagem útil.
     *
     * <p>A {@link MissingResourceException} original diz que o bundle não foi encontrado, mas
     * não diz onde ele era esperado — e a causa quase sempre é o arquivo não ter sido copiado
     * para {@code target/classes}, por estar fora de {@code src/main/resources}.
     */
    private ResourceBundle carregarTextos(Locale idioma) {
        try {
            return ResourceBundle.getBundle(BUNDLE, idioma);
        } catch (MissingResourceException e) {
            throw new MissingResourceException(
                    "bundle " + BUNDLE + " não encontrado para " + idioma.toLanguageTag()
                            + "; confira se os .properties estão em src/main/resources",
                    BUNDLE, "");
        }
    }

    /** Os idiomas atendidos, como lista legível para a mensagem de erro. */
    private String idiomasAtendidos() {
        return IDIOMAS.stream()
                .map(Locale::getLanguage)
                .collect(Collectors.joining(", "));
    }
}
