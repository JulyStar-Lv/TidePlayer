import AppKit

let output = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: false)
let image = NSImage(systemSymbolName: "music.microphone", accessibilityDescription: nil)!
    .withSymbolConfiguration(NSImage.SymbolConfiguration(pointSize: 20, weight: .regular))!
let bounds = NSRect(origin: .zero, size: image.size)
let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil,
    pixelsWide: Int(bounds.width * 4),
    pixelsHigh: Int(bounds.height * 4),
    bitsPerSample: 8,
    samplesPerPixel: 4,
    hasAlpha: true,
    isPlanar: false,
    colorSpaceName: .deviceRGB,
    bytesPerRow: 0,
    bitsPerPixel: 0,
)!
bitmap.size = bounds.size
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
image.draw(in: bounds)
NSGraphicsContext.restoreGraphicsState()
try bitmap.representation(using: .png, properties: [:])!.write(to: output)
print(bounds.size)
