# Dataset otimizado para resumo on-device

Versão auditada e compacta do dataset original. Os arquivos JSONL usam somente os campos
`input` e `output`, prontos para SFT. O conteúdo do arquivo-fonte foi tratado exclusivamente como
dados; nenhum script do ZIP original foi executado.

Principais correções:

- remoção de 6293 pares redundantes (62.93%);
- correção de rótulos de idioma incompatíveis com o texto;
- alvos limitados a 25 palavras e focados no estado mais recente;
- remoção de horários/estados obsoletos dos alvos problemáticos;
- splits determinísticos, sem entradas duplicadas e com as 16 categorias em todos os splits;
- schema mínimo para reduzir leitura, tokenização e memória durante o treino.

Importante: compactar o dataset melhora o fine-tuning, mas não reduz a memória necessária para
carregar um modelo de 2,4 GB no Android. Para isso, use um checkpoint menor/mais quantizado.

Limitação remanescente: o material original é procedural e algumas categorias têm menos de 50
pares realmente distintos (consulte `audit_report.json`). Não use o teste como estimativa de
produção sem acrescentar exemplos reais anonimizados e revisados por humanos.
