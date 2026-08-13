/** Turn stored prompt text into Markdown that actually reads as sections / lists / JSON. */
export function preparePromptMarkdown(raw: string): string {
  let text = raw.replace(/\r\n?/g, '\n').trim()
  if (!text) {
    return ''
  }
  if (!text.includes('\n')) {
    text = recoverCollapsedPrompt(text)
  }
  if (looksLikeBracketSections(text)) {
    text = formatBracketSections(text)
  }
  return text.replace(/\n{3,}/g, '\n\n').trim()
}

function looksLikeBracketSections(text: string): boolean {
  const hits = text.match(/(^|\n)\s*\[[^\]\n]{1,40}\]/g)
  return (hits?.length ?? 0) >= 2
}

function recoverCollapsedPrompt(text: string): string {
  return text
    .replace(/\s*\[([^\]]{1,40})\]\s*/g, '\n[$1]\n')
    .replace(/(\d{1,2})\.(?=[^\s\d])/g, '\n$1. ')
    .replace(/\s*(\{)/g, '\n$1')
    .replace(/(\})\s*/g, '$1\n')
    .trim()
}

function formatBracketSections(text: string): string {
  const lines = text.split('\n')
  const out: string[] = []
  let jsonBuf: string[] | null = null

  const flushJson = () => {
    if (!jsonBuf) {
      return
    }
    const rawJson = jsonBuf.join('\n').trim()
    jsonBuf = null
    if (!rawJson) {
      return
    }
    out.push('', '```json', prettyJson(rawJson), '```', '')
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (jsonBuf) {
      jsonBuf.push(line)
      if (trimmed.endsWith('}') && isBalancedJson(jsonBuf.join('\n'))) {
        flushJson()
      }
      continue
    }

    if (trimmed.startsWith('{')) {
      jsonBuf = [line]
      if (trimmed.endsWith('}') && isBalancedJson(trimmed)) {
        flushJson()
      }
      continue
    }

    const section = trimmed.match(/^\[([^\]]+)\]$/)
    if (section) {
      out.push('', `### ${section[1]}`, '')
      continue
    }

    const list = trimmed.match(/^(\d+)\.\s*(.+)$/)
    if (list) {
      out.push(`${list[1]}. ${list[2]}`)
      continue
    }

    out.push(line)
  }

  flushJson()
  return out.join('\n')
}

function prettyJson(raw: string): string {
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function isBalancedJson(raw: string): boolean {
  let depth = 0
  let inString = false
  let escape = false
  for (const ch of raw) {
    if (inString) {
      if (escape) {
        escape = false
      } else if (ch === '\\') {
        escape = true
      } else if (ch === '"') {
        inString = false
      }
      continue
    }
    if (ch === '"') {
      inString = true
    } else if (ch === '{') {
      depth += 1
    } else if (ch === '}') {
      depth -= 1
    }
  }
  return depth === 0 && !inString
}
