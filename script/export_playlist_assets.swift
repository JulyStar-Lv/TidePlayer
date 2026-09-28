import AppKit

// Keep the Music symbols' intrinsic bounds; export template images at 4x.
let output = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
for (name, symbol, pointSize) in [
    ("icon_playlist_favorites", "star.fill", CGFloat(90)),
    ("icon_playlist_placeholder", "music.note.list", CGFloat(90)),
    ("icon_playlist_sort_order", "arrow.up.arrow.down", CGFloat(14)),
] {
    let image = NSImage(systemSymbolName: symbol, accessibilityDescription: nil)!
        .withSymbolConfiguration(NSImage.SymbolConfiguration(pointSize: pointSize, weight: .regular))!
    let bounds = NSRect(origin: .zero, size: image.size)
    let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil,
        pixelsWide: Int(bounds.width * 4), pixelsHigh: Int(bounds.height * 4),
        bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
        colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)!
    bitmap.size = bounds.size
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
    image.draw(in: bounds)
    NSGraphicsContext.restoreGraphicsState()
    try bitmap.representation(using: .png, properties: [:])!
        .write(to: output.appendingPathComponent(name + ".png"))
}
