import AppKit
import Foundation

let root = "/Users/yingxu/public-repos/pet-social-app/android/share/release/final_screenshots"

let phoneScreens: [String] = [
    "play_asset_screenshot_01_listings_phone_1080x2340.png",
    "play_asset_screenshot_02_community_phone_1080x2340.png",
    "play_asset_screenshot_03_barkai_phone_1080x2340.png",
    "play_asset_screenshot_04_messages_phone_1080x2340.png",
    "play_asset_screenshot_05_home_phone_1080x2340.png",
]

func topRect(canvasHeight: CGFloat, x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat) -> NSRect {
    NSRect(x: x, y: canvasHeight - y - height, width: width, height: height)
}

func writeImage(_ rep: NSBitmapImageRep, to path: String) throws {
    guard let data = rep.representation(using: .png, properties: [:]) else {
        throw NSError(domain: "asset.fix", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unable to encode PNG"])
    }
    try data.write(to: URL(fileURLWithPath: path))
}

func drawHeaderCard(canvasHeight: CGFloat, iconPath: String) {
    let headerRect = topRect(canvasHeight: canvasHeight, x: 32, y: 132, width: 1016, height: 258)
    let gradient = NSGradient(colors: [
        NSColor(calibratedRed: 110/255, green: 168/255, blue: 135/255, alpha: 1),
        NSColor(calibratedRed: 143/255, green: 191/255, blue: 163/255, alpha: 1),
        NSColor(calibratedRed: 184/255, green: 216/255, blue: 197/255, alpha: 1),
    ])!
    NSGraphicsContext.saveGraphicsState()
    let clip = NSBezierPath(roundedRect: headerRect, xRadius: 34, yRadius: 34)
    clip.addClip()
    gradient.draw(in: headerRect, angle: 0)
    NSGraphicsContext.restoreGraphicsState()

    // Small left icon badge.
    let badgeRect = topRect(canvasHeight: canvasHeight, x: 72, y: 170, width: 54, height: 54)
    NSColor.white.withAlphaComponent(0.90).setFill()
    NSBezierPath(ovalIn: badgeRect).fill()
    if let icon = NSImage(contentsOfFile: iconPath) {
        icon.draw(in: topRect(canvasHeight: canvasHeight, x: 74, y: 172, width: 50, height: 50))
    }

    let titleAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 52, weight: .semibold),
        .foregroundColor: NSColor.white,
    ]
    let subtitleAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 28, weight: .regular),
        .foregroundColor: NSColor.white.withAlphaComponent(0.88),
    ]
    let modeAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 26, weight: .medium),
        .foregroundColor: NSColor.white.withAlphaComponent(0.96),
    ]

    NSAttributedString(string: "BarkWise", attributes: titleAttrs)
        .draw(in: topRect(canvasHeight: canvasHeight, x: 142, y: 162, width: 300, height: 62))
    NSAttributedString(string: "Dog owners, groups, and trusted local care", attributes: subtitleAttrs)
        .draw(in: topRect(canvasHeight: canvasHeight, x: 142, y: 220, width: 620, height: 36))
    NSAttributedString(string: "Mode: Beta 1", attributes: modeAttrs)
        .draw(in: topRect(canvasHeight: canvasHeight, x: 142, y: 262, width: 240, height: 34))

    // Right status chip.
    let chipRect = topRect(canvasHeight: canvasHeight, x: 742, y: 176, width: 274, height: 90)
    NSColor.white.withAlphaComponent(0.28).setFill()
    NSBezierPath(roundedRect: chipRect, xRadius: 45, yRadius: 45).fill()
    NSColor.white.withAlphaComponent(0.90).setFill()
    NSBezierPath(ovalIn: topRect(canvasHeight: canvasHeight, x: 757, y: 190, width: 60, height: 60)).fill()
    if let icon = NSImage(contentsOfFile: iconPath) {
        icon.draw(in: topRect(canvasHeight: canvasHeight, x: 761, y: 194, width: 52, height: 52))
    }
    let chipAttrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: 26, weight: .semibold),
        .foregroundColor: NSColor(calibratedRed: 41/255, green: 58/255, blue: 50/255, alpha: 1),
    ]
    NSAttributedString(string: "New this\nweek", attributes: chipAttrs)
        .draw(in: topRect(canvasHeight: canvasHeight, x: 826, y: 186, width: 170, height: 70))
}

func patchPhoneScreenshot(path: String, includeSearchFieldFix: Bool, iconPath: String) throws {
    guard let src = NSImage(contentsOfFile: path),
          let tiff = src.tiffRepresentation,
          let srcRep = NSBitmapImageRep(data: tiff) else {
        throw NSError(domain: "asset.fix", code: 2, userInfo: [NSLocalizedDescriptionKey: "Failed to read image: \(path)"])
    }

    let w = srcRep.pixelsWide
    let h = srcRep.pixelsHigh

    guard let outRep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: w,
        pixelsHigh: h,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else {
        throw NSError(domain: "asset.fix", code: 3, userInfo: [NSLocalizedDescriptionKey: "Failed to create bitmap"])
    }

    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: outRep)

    src.draw(in: NSRect(x: 0, y: 0, width: w, height: h))

    // Redraw the whole header card cleanly so text/casing artifacts are removed.
    drawHeaderCard(canvasHeight: CGFloat(h), iconPath: iconPath)

    // Messages tab: Search: Dog or Human -> Search dog or human
    if includeSearchFieldFix {
        let inputRect = topRect(canvasHeight: CGFloat(h), x: 32, y: 598, width: 1016, height: 146)
        NSColor(calibratedRed: 241/255, green: 246/255, blue: 243/255, alpha: 1).setFill()
        let inputPath = NSBezierPath(roundedRect: inputRect, xRadius: 8, yRadius: 8)
        inputPath.fill()
        NSColor(calibratedRed: 212/255, green: 177/255, blue: 149/255, alpha: 1).setStroke()
        inputPath.lineWidth = 3
        inputPath.stroke()

        let searchAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 42, weight: .regular),
            .foregroundColor: NSColor(calibratedRed: 72/255, green: 82/255, blue: 76/255, alpha: 1),
        ]
        NSAttributedString(string: "Search dog or human", attributes: searchAttrs)
            .draw(in: topRect(canvasHeight: CGFloat(h), x: 72, y: 644, width: 500, height: 48))
    }

    NSGraphicsContext.restoreGraphicsState()
    try writeImage(outRep, to: path)
}

do {
    let iconPath = "/Users/yingxu/public-repos/pet-social-app/android/share/release/play_asset_icon_original_current_512.png"
    for name in phoneScreens {
        let fullPath = "\(root)/\(name)"
        try patchPhoneScreenshot(
            path: fullPath,
            includeSearchFieldFix: name.contains("_04_messages_"),
            iconPath: iconPath
        )
        print("patched \(fullPath)")
    }
} catch {
    fputs("asset language fix failed: \(error)\n", stderr)
    exit(1)
}
