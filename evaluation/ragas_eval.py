"""
RAG 评估脚本（DeepSeek 裁判版）

评估维度：
  1. Faithfulness（忠实度）：回答是否可以从上下文中找到依据
  2. Answer Relevancy（答案相关性）：回答是否切题
  3. Context Precision（上下文精确率）：检索结果是否相关

使用：
  $env:DEEPSEEK_API_KEY = "sk-你的key"
  python ragas_eval.py
"""

import json, os, time, requests
from openai import OpenAI

RAG_API = "http://localhost:9090/api/rag/chat/sync"
TEST_FILE = "test_dataset.json"

DEEPSEEK_KEY = os.getenv("DEEPSEEK_API_KEY", "")
if not DEEPSEEK_KEY:
    print("请设置: $env:DEEPSEEK_API_KEY = 'sk-你的key'")
    exit(1)

llm = OpenAI(api_key=DEEPSEEK_KEY, base_url="https://api.deepseek.com")


def get_rag_result(question):
    try:
        resp = requests.post(RAG_API,
            json={"question": question, "sessionId": "eval"}, timeout=120)
        data = resp.json()
        ctxs = data.get("contexts", [])
        return {
            "answer": data.get("answer", ""),
            "contexts": [c.get("content", "") for c in ctxs] if ctxs else [],
        }
    except Exception as e:
        return {"answer": f"[ERROR] {e}", "contexts": []}


JUDGE_PROMPT = """你是一个 RAG 系统评估专家。请对以下问答结果打分。

【用户问题】
{question}

【标准答案】
{ground_truth}

【RAG 系统回答】
{answer}

【检索到的参考上下文】
{contexts}

从以下三个维度打分（0-10，可为小数）：

1. faithfulness（忠实度）：回答中的每个声明是否都能从参考上下文中找到依据？
   10 = 全部有依据，零编造 | 7 = 大部分有依据 | 4 = 较多无法验证 | 0 = 完全编造

2. answer_relevancy（答案相关性）：回答是否直接、完整地回答了问题？
   10 = 完全切题 | 7 = 基本回答 | 4 = 部分偏离 | 0 = 答非所问

3. context_precision（上下文精确率）：检索到的上下文中，真正相关的占比？
   10 = 全部相关 | 7 = 大部分相关 | 4 = 少数相关 | 0 = 全都不相关

严格输出 JSON，不要其他内容：
{{"faithfulness": 8.5, "answer_relevancy": 7.0, "context_precision": 9.0, "comment": "一句话简评"}}"""


def judge(question, ground_truth, answer, contexts):
    ctx_text = "\n---\n".join(contexts) if contexts else "(无)"
    prompt = JUDGE_PROMPT.format(
        question=question,
        ground_truth=ground_truth,
        answer=answer[:2000],
        contexts=ctx_text[:3000],
    )
    try:
        resp = llm.chat.completions.create(
            model="deepseek-chat",
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
        )
        text = resp.choices[0].message.content.strip()
        if "```" in text:
            text = text.split("```")[1].split("```")[0]
            if text.startswith("json"): text = text[4:]
        return json.loads(text)
    except Exception as e:
        return {"faithfulness": 0, "answer_relevancy": 0, "context_precision": 0, "comment": str(e)}


def main():
    print("=" * 60)
    print("  RAG 评估（DeepSeek 裁判）")
    print("=" * 60)

    with open(TEST_FILE, "r", encoding="utf-8") as f:
        tests = json.load(f)
    print(f"测试集: {len(tests)} 条\n")

    scores = {"faithfulness": [], "answer_relevancy": [], "context_precision": []}

    for i, tc in enumerate(tests):
        q, gt = tc["question"], tc["ground_truth"]
        print(f"[{i+1}/{len(tests)}] {q}")

        r = get_rag_result(q)
        s = judge(q, gt, r["answer"], r["contexts"])

        for k in scores:
            scores[k].append(s.get(k, 0))

        print(f"    忠实度={s['faithfulness']:.1f}  相关性={s['answer_relevancy']:.1f}  上下文精确率={s['context_precision']:.1f}  |  {s.get('comment', '')}")
        time.sleep(0.3)

    print(f"\n{'=' * 60}")
    print("  基线报告")
    print(f"{'=' * 60}")

    avg = {}
    labels = {"faithfulness": "忠实度", "answer_relevancy": "答案相关性", "context_precision": "上下文精确率"}
    for k, v in scores.items():
        avg[k] = round(sum(v) / len(v), 2)
        bar = "█" * int(avg[k]) + "░" * (10 - int(avg[k]))
        print(f"  {labels[k]:12s}  {avg[k]:.1f}/10  {bar}")

    report = {"timestamp": time.strftime("%Y-%m-%d %H:%M:%S"), "test_cases": len(tests), "averages": avg}
    with open("baseline_report.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"\n报告: baseline_report.json")


if __name__ == "__main__":
    main()
