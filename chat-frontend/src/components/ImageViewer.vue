<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  src: String
})
const emit = defineEmits(['close'])

const scale = ref(1)
const translate = ref({ x: 0, y: 0 })
const dragging = ref(false)
let last = { x: 0, y: 0 }

function onKey(e) {
  if (e.key === 'Escape') emit('close')
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))

function zoom(delta) {
  scale.value = Math.min(6, Math.max(0.5, scale.value + delta))
}

function onWheel(e) {
  zoom(e.deltaY < 0 ? 0.2 : -0.2)
}

function reset() {
  scale.value = 1
  translate.value = { x: 0, y: 0 }
}

function onMouseDown(e) {
  // 点在图片之外的空白区域 -> 关闭
  if (e.target === e.currentTarget) {
    emit('close')
    return
  }
  // 点在图片上 -> 开始拖动
  dragging.value = true
  last = { x: e.clientX, y: e.clientY }
}
function onMouseMove(e) {
  if (!dragging.value) return
  translate.value.x += e.clientX - last.x
  translate.value.y += e.clientY - last.y
  last = { x: e.clientX, y: e.clientY }
}
function onMouseUp() { dragging.value = false }

function onDoubleClick() {
  if (scale.value > 1) reset()
  else zoom(1)
}
</script>

<template>
  <div class="img-viewer" @mousedown.self="emit('close')" @wheel.prevent="onWheel">
    <div class="viewer-toolbar">
      <span class="viewer-hint">滚轮缩放 · 拖拽移动 · 双击还原 · Esc 关闭</span>
      <div class="viewer-btns">
        <button @click="zoom(0.3)">＋ 放大</button>
        <button @click="zoom(-0.3)">－ 缩小</button>
        <button @click="reset">适应</button>
        <button class="viewer-close" @click="emit('close')">✕ 关闭</button>
      </div>
    </div>
    <div
      class="viewer-canvas"
      :class="{ dragging }"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
      @dblclick="onDoubleClick"
    >
      <img
        :src="src"
        alt="预览"
        draggable="false"
        :style="{ transform: `scale(${scale}) translate(${translate.x}px, ${translate.y}px)` }"
      />
    </div>
  </div>
</template>
