#!/usr/bin/env python3
"""Audit and compact the synthetic notification dataset for SFT/evaluation.

The source archive is treated strictly as data. This script does not import or execute any
Python contained in it. It reads the raw JSONL member directly from the ZIP.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import unicodedata
import zipfile
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any, Iterable


RAW_MEMBER = "dataset/raw/notifications_full.jsonl"
REQUIRED_FIELDS = {
    "id",
    "language",
    "category",
    "notifications",
    "summary",
    "key_facts",
    "obsolete_facts",
}
MAX_OUTPUT_WORDS = 25


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--zip-output", type=Path)
    return parser.parse_args()


def normalized(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def read_source(source_zip: Path) -> Iterable[dict[str, Any]]:
    with zipfile.ZipFile(source_zip) as archive:
        if RAW_MEMBER not in archive.namelist():
            raise ValueError(f"Membro obrigatório ausente: {RAW_MEMBER}")
        with archive.open(RAW_MEMBER) as raw:
            for line_number, encoded_line in enumerate(raw, 1):
                if not encoded_line.strip():
                    continue
                try:
                    row = json.loads(encoded_line)
                except (UnicodeDecodeError, json.JSONDecodeError) as exc:
                    raise ValueError(f"JSON inválido na linha {line_number}: {exc}") from exc
                validate_source_row(row, line_number)
                yield row


def audit_source_splits(source_zip: Path) -> dict[str, Any]:
    result: dict[str, Any] = {}
    with zipfile.ZipFile(source_zip) as archive:
        for split in ("train", "validation", "test"):
            member = f"dataset/processed/{split}.jsonl"
            categories: set[str] = set()
            examples = 0
            with archive.open(member) as raw:
                for encoded_line in raw:
                    if not encoded_line.strip():
                        continue
                    row = json.loads(encoded_line)
                    examples += 1
                    categories.add(row["category"])
            result[split] = {
                "examples": examples,
                "category_count": len(categories),
                "categories": sorted(categories),
            }
    return result


def validate_source_row(row: dict[str, Any], line_number: int) -> None:
    missing = REQUIRED_FIELDS.difference(row)
    if missing:
        raise ValueError(f"Linha {line_number}: campos ausentes: {sorted(missing)}")
    if not isinstance(row["notifications"], list) or not row["notifications"]:
        raise ValueError(f"Linha {line_number}: notifications deve ser uma lista não vazia")
    for notification in row["notifications"]:
        if not isinstance(notification, dict) or not all(
            isinstance(notification.get(field), str)
            for field in ("timestamp", "sender", "text")
        ):
            raise ValueError(f"Linha {line_number}: notificação inválida")
    if not isinstance(row["summary"], str) or not row["summary"].strip():
        raise ValueError(f"Linha {line_number}: summary vazio")


def actual_language(row: dict[str, Any]) -> str:
    """Repair labels according to the language branches used by the source generator."""
    category = row["category"]
    declared = row["language"]
    if category == "instant_messaging":
        return declared if declared in {"pt-BR", "en-US"} else "es-ES"
    if category == "email":
        return "pt-BR" if declared == "pt-BR" else "en-US"
    return "pt-BR"


def compact_summary(row: dict[str, Any]) -> str:
    summary = " ".join(row["summary"].split())
    notifications = row["notifications"]
    category = row["category"]

    if category == "instant_messaging":
        summary = re.split(r",\s*(?:após|after|luego de)\b", summary, maxsplit=1)[0]
        summary = summary.rstrip(". ") + "."
        replacements = {
            "ir ao academia": "ir à academia",
            "ir ao consulta": "ir à consulta",
            "ir ao festa": "ir à festa",
            "ir ao reunião": "ir à reunião",
            "ir ao viagem": "ir à viagem",
            "reunião do academia": "reunião da academia",
            "reunião do consulta": "reunião da consulta",
            "reunião do festa": "reunião da festa",
            "reunião do reunião": "reunião",
            "reunião do viagem": "reunião da viagem",
            "The meeting meeting": "The meeting",
            "meeting for meeting": "meeting tomorrow",
        }
        for old, new in replacements.items():
            summary = summary.replace(old, new)

    elif category == "ecommerce" and notifications[-1].get("title") == "Entregue":
        order = re.search(r"#\d+", notifications[-1]["text"])
        summary = f"O pedido {order.group(0) + ' ' if order else ''}foi entregue com sucesso."

    elif category == "food_delivery" and notifications[-1].get("title") == "Chegou!":
        restaurant = re.match(r"(.+?) está preparando", notifications[0]["text"])
        summary = (
            f"O entregador do pedido de {restaurant.group(1)} chegou."
            if restaurant
            else "O entregador chegou com o pedido."
        )

    elif category == "transport" and notifications[-1].get("title") == "Novo motorista":
        summary = "Nova corrida: " + notifications[-1]["text"].rstrip(".") + "."

    elif category == "education" and "foi remarcada para" in notifications[-1]["text"]:
        summary = notifications[-1]["text"].replace("foi remarcada para", "será")

    elif category == "calendar" and notifications[-1].get("title") == "Evento Cancelado":
        summary = notifications[-1]["text"]

    elif category == "work" and notifications[-1].get("title") == "CI Passed":
        pr = re.search(r"PR #\d+", notifications[-1]["text"])
        fixer = notifications[-2]["sender"] if len(notifications) > 2 else None
        if pr and fixer:
            summary = f"O pipeline do {pr.group(0)} passou após a correção de {fixer}."

    word_count = len(summary.split())
    if word_count > MAX_OUTPUT_WORDS:
        raise ValueError(
            f"{row['id']}: saída com {word_count} palavras; requer revisão em vez de truncamento"
        )
    return summary


def model_input(row: dict[str, Any]) -> str:
    lines = []
    for notification in row["notifications"]:
        prefix = f"[{notification['timestamp']}] {notification['sender']}"
        if notification.get("title"):
            prefix += f" — {notification['title']}"
        lines.append(f"{prefix}: {notification['text'].strip()}")
    return "\n".join(lines)


def canonical_pair_key(row: dict[str, Any], output: str) -> str:
    # Clock values are not useful variants when notification text and target are identical.
    input_without_clock = "\n".join(
        f"{item['sender']}|{item.get('title') or ''}|{item['text']}"
        for item in row["notifications"]
    )
    return normalized(input_without_clock) + "\n=>" + normalized(output)


def stable_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def optimize(rows: Iterable[dict[str, Any]]) -> tuple[list[dict[str, str]], dict[str, Any]]:
    seen: set[str] = set()
    optimized: list[dict[str, str]] = []
    source_languages: Counter[str] = Counter()
    repaired_languages: Counter[str] = Counter()
    category_counts: Counter[str] = Counter()
    source_count = 0

    for row in rows:
        source_count += 1
        source_languages[row["language"]] += 1
        language = actual_language(row)
        if language != row["language"]:
            repaired_languages[f"{row['language']}->{language}"] += 1
        output = compact_summary(row)
        pair_key = canonical_pair_key(row, output)
        if pair_key in seen:
            continue
        seen.add(pair_key)
        category_counts[row["category"]] += 1
        optimized.append(
            {
                "id": row["id"],
                "language": language,
                "category": row["category"],
                "input": model_input(row),
                "output": output,
            }
        )

    report = {
        "source_examples": source_count,
        "optimized_examples": len(optimized),
        "duplicates_removed": source_count - len(optimized),
        "reduction_percent": round((source_count - len(optimized)) * 100 / source_count, 2),
        "source_language_labels": dict(sorted(source_languages.items())),
        "language_labels_repaired": dict(sorted(repaired_languages.items())),
        "optimized_categories": dict(sorted(category_counts.items())),
        "low_diversity_categories_under_50_unique_pairs": {
            category: count
            for category, count in sorted(category_counts.items())
            if count < 50
        },
        "max_output_words": max(len(item["output"].split()) for item in optimized),
        "average_output_words": round(
            sum(len(item["output"].split()) for item in optimized) / len(optimized), 2
        ),
    }
    return optimized, report


def split_rows(rows: list[dict[str, str]]) -> dict[str, list[dict[str, str]]]:
    by_category: dict[str, list[dict[str, str]]] = defaultdict(list)
    for row in rows:
        by_category[row["category"]].append(row)

    result = {"train": [], "validation": [], "test": []}
    for category_rows in by_category.values():
        category_rows.sort(key=lambda row: stable_hash(row["id"] + row["input"]))
        count = len(category_rows)
        validation_count = max(1, round(count * 0.1))
        test_count = max(1, round(count * 0.1))
        train_count = count - validation_count - test_count
        if train_count < 1:
            raise ValueError("Categoria sem exemplos suficientes para os três splits")
        result["train"].extend(category_rows[:train_count])
        result["validation"].extend(
            category_rows[train_count : train_count + validation_count]
        )
        result["test"].extend(category_rows[train_count + validation_count :])

    for rows_in_split in result.values():
        rows_in_split.sort(key=lambda row: stable_hash(row["input"]))
    return result


def training_record(row: dict[str, str]) -> dict[str, str]:
    # Only fields consumed by SFT are persisted, avoiding metadata tokenization/collation overhead.
    return {"input": row["input"], "output": row["output"]}


def write_jsonl(path: Path, rows: Iterable[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as output:
        for row in rows:
            output.write(json.dumps(training_record(row), ensure_ascii=False, separators=(",", ":")))
            output.write("\n")


def validate_output(splits: dict[str, list[dict[str, str]]]) -> dict[str, Any]:
    categories_by_split = {
        name: sorted({row["category"] for row in rows}) for name, rows in splits.items()
    }
    expected_categories = set(categories_by_split["train"])
    if any(set(categories) != expected_categories for categories in categories_by_split.values()):
        raise ValueError("Nem todos os splits contêm todas as categorias")

    input_sets = {
        name: {normalized(row["input"]) for row in rows} for name, rows in splits.items()
    }
    overlap = {
        "train_validation": len(input_sets["train"] & input_sets["validation"]),
        "train_test": len(input_sets["train"] & input_sets["test"]),
        "validation_test": len(input_sets["validation"] & input_sets["test"]),
    }
    if any(overlap.values()):
        raise ValueError(f"Entradas duplicadas entre splits: {overlap}")

    return {
        "split_examples": {name: len(rows) for name, rows in splits.items()},
        "categories_per_split": categories_by_split,
        "normalized_input_overlap": overlap,
    }


def write_readme(output_dir: Path, report: dict[str, Any]) -> None:
    text = f"""# Dataset otimizado para resumo on-device

Versão auditada e compacta do dataset original. Os arquivos JSONL usam somente os campos
`input` e `output`, prontos para SFT. O conteúdo do arquivo-fonte foi tratado exclusivamente como
dados; nenhum script do ZIP original foi executado.

Principais correções:

- remoção de {report['duplicates_removed']} pares redundantes ({report['reduction_percent']}%);
- correção de rótulos de idioma incompatíveis com o texto;
- alvos limitados a {MAX_OUTPUT_WORDS} palavras e focados no estado mais recente;
- remoção de horários/estados obsoletos dos alvos problemáticos;
- splits determinísticos, sem entradas duplicadas e com as 16 categorias em todos os splits;
- schema mínimo para reduzir leitura, tokenização e memória durante o treino.

Importante: compactar o dataset melhora o fine-tuning, mas não reduz a memória necessária para
carregar um modelo de 2,4 GB no Android. Para isso, use um checkpoint menor/mais quantizado.

Limitação remanescente: o material original é procedural e algumas categorias têm menos de 50
pares realmente distintos (consulte `audit_report.json`). Não use o teste como estimativa de
produção sem acrescentar exemplos reais anonimizados e revisados por humanos.
"""
    (output_dir / "README.md").write_text(text, encoding="utf-8")


def main() -> None:
    args = parse_args()
    if args.output_dir.exists():
        raise SystemExit(f"O diretório de saída já existe: {args.output_dir}")
    args.output_dir.mkdir(parents=True)

    rows, report = optimize(read_source(args.source_zip))
    report["source_splits"] = audit_source_splits(args.source_zip)
    splits = split_rows(rows)
    report.update(validate_output(splits))

    for name, split in splits.items():
        write_jsonl(args.output_dir / f"{name}.jsonl", split)
    (args.output_dir / "audit_report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    write_readme(args.output_dir, report)

    if args.zip_output:
        if args.zip_output.exists():
            raise SystemExit(f"O ZIP de saída já existe: {args.zip_output}")
        shutil.make_archive(str(args.zip_output.with_suffix("")), "zip", args.output_dir)

    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
