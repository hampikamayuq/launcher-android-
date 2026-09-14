# O EvalEx é compilado com Lombok e carrega referências à anotação
# lombok.Generated (retenção CLASS, sem efeito em runtime). O R8 não a
# encontra no classpath e pararia o build; ignorar o aviso é o esperado.
-dontwarn lombok.Generated
