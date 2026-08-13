import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { preparePromptMarkdown } from './promptMarkdownFormat'

export { preparePromptMarkdown }

export function renderPromptMarkdown(content: string | null | undefined): string {
  if (content == null || !content.trim()) {
    return ''
  }
  try {
    const markdown = preparePromptMarkdown(content)
    const html = marked.parse(markdown, { async: false, gfm: true, breaks: true })
    return DOMPurify.sanitize(typeof html === 'string' ? html : String(html))
  } catch {
    return `<pre class="prompt-md-fallback">${escapeHtml(content)}</pre>`
  }
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}
