import { afterEach, expect, it, vi } from 'vitest'
import { saveBlob } from './download'

afterEach(() => vi.useRealTimers())

it('downloads the blob with its file name and releases the URL afterwards', () => {
  vi.useFakeTimers()
  URL.createObjectURL = vi.fn(() => 'blob:file')
  URL.revokeObjectURL = vi.fn()
  const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
    expect(this.download).toBe('factura.pdf')
    expect(this.href).toBe('blob:file')
    expect(this.isConnected).toBe(true)
  })
  saveBlob(new Blob(['%PDF']), 'factura.pdf')
  expect(click).toHaveBeenCalledOnce()
  expect(document.querySelector('a')).toBeNull()
  expect(URL.revokeObjectURL).not.toHaveBeenCalled()
  vi.advanceTimersByTime(1_000)
  expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:file')
  click.mockRestore()
})
