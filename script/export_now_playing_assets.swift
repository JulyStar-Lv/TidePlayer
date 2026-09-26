import AppKit

// Export native template artwork at 4x; preserve its intrinsic point bounds.
let output = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
let symbols: [(String, String, CGFloat)] = [
    ("icon_now_playing_close", "xmark", 14),
    ("icon_now_playing_translation", "translate", 14),
    ("icon_now_playing_lyrics", "quote.bubble.fill", 16),
]
var images = symbols.map { file, symbol, size in
    (file, NSImage(systemSymbolName: symbol, accessibilityDescription: nil)!
        .withSymbolConfiguration(NSImage.SymbolConfiguration(pointSize: size, weight: .medium))!)
}
// Music's now-playing button uses pip.enter, including the diagonal inward arrow.
// At 17 pt regular its 26 x 20 pt canvas contains a 21.5 x 17.5 pt glyph.
images.append(("icon_now_playing_mini",
    NSImage(systemSymbolName: "pip.enter", accessibilityDescription: nil)!
        .withSymbolConfiguration(NSImage.SymbolConfiguration(pointSize: 17, weight: .regular))!))
for (name, image) in images {
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
    print(name, bounds.size)
}
