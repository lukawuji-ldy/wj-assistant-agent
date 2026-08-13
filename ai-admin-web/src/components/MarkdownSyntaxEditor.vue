<template>
  <div ref="host" class="md-syntax-editor" />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { EditorView, placeholder } from '@codemirror/view'
import { markdown } from '@codemirror/lang-markdown'

const content = defineModel<string>({ required: true })
const host = ref<HTMLDivElement | null>(null)
let view: EditorView | null = null

const editorTheme = EditorView.theme({
  '&': {
    height: '52vh',
    border: '1px solid #e5e7eb',
    borderRadius: '6px',
    overflow: 'hidden',
    backgroundColor: '#fafbfc',
  },
  '&.cm-focused': {
    outline: 'none',
    borderColor: '#409eff',
  },
  '.cm-scroller': {
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
    fontSize: '13px',
    lineHeight: '1.55',
  },
  '.cm-gutters': {
    backgroundColor: '#f3f4f6',
    borderRight: '1px solid #e5e7eb',
    color: '#9ca3af',
  },
  '.cm-activeLine': {
    backgroundColor: '#f3f4f6',
  },
  '.cm-activeLineGutter': {
    backgroundColor: '#e5e7eb',
  },
})

onMounted(() => {
  if (!host.value) {
    return
  }
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: content.value,
      extensions: [
        basicSetup,
        markdown(),
        EditorView.lineWrapping,
        placeholder('支持 Markdown 语法，例如 ### 角色'),
        editorTheme,
        EditorView.updateListener.of((update) => {
          if (update.docChanged) {
            content.value = update.state.doc.toString()
          }
        }),
      ],
    }),
  })
})

watch(content, (value) => {
  if (!view) {
    return
  }
  const current = view.state.doc.toString()
  if (value !== current) {
    view.dispatch({
      changes: { from: 0, to: current.length, insert: value },
    })
  }
})

onBeforeUnmount(() => {
  view?.destroy()
  view = null
})
</script>
