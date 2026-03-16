import AppKit
import Foundation

private let releaseDir = "/Users/yingxu/public-repos/pet-social-app/android/share/release"
private let finalScreenshotsDir = "\(releaseDir)/final_screenshots"

private func withBitmap(
    width: Int,
    height: Int,
    draw: () -> Void
) -> NSBitmapImageRep {
    guard let rep = NSBitmapImageRep(
        bitmapDataPlanes: nil,
        pixelsWide: width,
        pixelsHigh: height,
        bitsPerSample: 8,
        samplesPerPixel: 4,
        hasAlpha: true,
        isPlanar: false,
        colorSpaceName: .deviceRGB,
        bytesPerRow: 0,
        bitsPerPixel: 0
    ) else {
        fatalError("Failed to create bitmap rep")
    }
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    return rep
}

private func writePng(_ rep: NSBitmapImageRep, to path: String) throws {
    guard let data = rep.representation(using: .png, properties: [:]) else {
        fatalError("Failed to encode PNG: \(path)")
    }
    try data.write(to: URL(fileURLWithPath: path))
}

private func topRect(canvasHeight: Int, _ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> NSRect {
    NSRect(x: x, y: CGFloat(canvasHeight) - y - h, width: w, height: h)
}

private func drawRoundedRect(_ rect: NSRect, radius: CGFloat, fill: NSColor, stroke: NSColor? = nil, lineWidth: CGFloat = 0) {
    fill.setFill()
    let path = NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius)
    path.fill()
    if let stroke {
        stroke.setStroke()
        path.lineWidth = lineWidth
        path.stroke()
    }
}

private func drawFeatureGraphic() throws {
    let width = 1024
    let height = 500
    let outputPath = "\(releaseDir)/play_asset_feature_graphic_1024x500.png"
    let legacyOut = "\(releaseDir)/play_feature_graphic_1024x500.png"
    let iconPath = "\(releaseDir)/play_asset_icon_original_current_512.png"

    let rep = withBitmap(width: width, height: height) {
        NSColor(calibratedRed: 246/255, green: 251/255, blue: 247/255, alpha: 1).setFill()
        NSBezierPath(rect: NSRect(x: 0, y: 0, width: width, height: height)).fill()

        let headerRect = topRect(canvasHeight: height, 24, 24, 976, 118)
        NSGraphicsContext.saveGraphicsState()
        let headerClip = NSBezierPath(roundedRect: headerRect, xRadius: 22, yRadius: 22)
        headerClip.addClip()
        let gradient = NSGradient(colors: [
            NSColor(calibratedRed: 110/255, green: 168/255, blue: 135/255, alpha: 1),
            NSColor(calibratedRed: 143/255, green: 191/255, blue: 163/255, alpha: 1),
            NSColor(calibratedRed: 184/255, green: 216/255, blue: 197/255, alpha: 1),
        ])!
        gradient.draw(in: headerRect, angle: 0)
        NSGraphicsContext.restoreGraphicsState()

        if let icon = NSImage(contentsOfFile: iconPath) {
            icon.draw(in: topRect(canvasHeight: height, 44, 48, 50, 50))
        }

        let titleAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 30, weight: .semibold),
            .foregroundColor: NSColor.white,
        ]
        let subtitleAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 17, weight: .regular),
            .foregroundColor: NSColor.white.withAlphaComponent(0.88),
        ]
        let modeAttrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.systemFont(ofSize: 14, weight: .medium),
            .foregroundColor: NSColor.white.withAlphaComponent(0.96),
        ]
        NSAttributedString(string: "BarkWise", attributes: titleAttrs).draw(in: topRect(canvasHeight: height, 106, 48, 280, 38))
        NSAttributedString(string: "Dog owners, groups, and trusted local care", attributes: subtitleAttrs)
            .draw(in: topRect(canvasHeight: height, 106, 86, 560, 24))
        NSAttributedString(string: "Mode: Beta 1", attributes: modeAttrs)
            .draw(in: topRect(canvasHeight: height, 106, 111, 180, 18))

        let portraitOuter = topRect(canvasHeight: height, 24, 162, 332, 314)
        drawRoundedRect(
            portraitOuter,
            radius: 34,
            fill: .white,
            stroke: NSColor(calibratedRed: 220/255, green: 237/255, blue: 227/255, alpha: 1),
            lineWidth: 2
        )
        let portraitInner = topRect(canvasHeight: height, 52, 190, 276, 258)
        drawRoundedRect(
            portraitInner,
            radius: 28,
            fill: NSColor(calibratedRed: 244/255, green: 248/255, blue: 245/255, alpha: 1)
        )
        if let icon = NSImage(contentsOfFile: iconPath) {
            icon.draw(in: topRect(canvasHeight: height, 69, 203, 242, 242))
        }

        NSColor(calibratedRed: 233/255, green: 244/255, blue: 238/255, alpha: 1).setFill()
        NSBezierPath(roundedRect: topRect(canvasHeight: height, 404, 236, 546, 24), xRadius: 12, yRadius: 12).fill()
        NSBezierPath(roundedRect: topRect(canvasHeight: height, 404, 276, 476, 24), xRadius: 12, yRadius: 12).fill()
        NSBezierPath(roundedRect: topRect(canvasHeight: height, 404, 316, 508, 24), xRadius: 12, yRadius: 12).fill()
    }

    try writePng(rep, to: outputPath)
    try writePng(rep, to: legacyOut)
    print(outputPath)
    print(legacyOut)
}

private func generateTabletScreenshots() throws {
    let sourceItems: [(source: String, slug: String)] = [
        ("play_asset_screenshot_01_listings_phone_1080x2340.png", "listings"),
        ("play_asset_screenshot_02_community_phone_1080x2340.png", "community"),
        ("play_asset_screenshot_03_barkai_phone_1080x2340.png", "barkai"),
        ("play_asset_screenshot_04_messages_phone_1080x2340.png", "messages"),
        ("play_asset_screenshot_05_home_phone_1080x2340.png", "home"),
    ]
    let targetSizes: [(name: String, width: Int, height: Int)] = [
        ("tablet7", 1200, 1920),
        ("tablet10", 1600, 2560),
    ]

    for target in targetSizes {
        for (index, item) in sourceItems.enumerated() {
            let sourcePath = "\(finalScreenshotsDir)/\(item.source)"
            guard let screenshot = NSImage(contentsOfFile: sourcePath) else {
                continue
            }
            let width = target.width
            let height = target.height
            let rep = withBitmap(width: width, height: height) {
                NSColor(calibratedRed: 246/255, green: 251/255, blue: 247/255, alpha: 1).setFill()
                NSBezierPath(rect: NSRect(x: 0, y: 0, width: width, height: height)).fill()

                let cardInsetX = CGFloat(width) * 0.06
                let cardInsetTop = CGFloat(height) * 0.04
                let cardInsetBottom = CGFloat(height) * 0.04
                let cardW = CGFloat(width) - cardInsetX * 2
                let cardH = CGFloat(height) - cardInsetTop - cardInsetBottom
                let outerRect = topRect(canvasHeight: height, cardInsetX, cardInsetTop, cardW, cardH)

                drawRoundedRect(
                    outerRect,
                    radius: CGFloat(width) * 0.035,
                    fill: .white,
                    stroke: NSColor(calibratedRed: 220/255, green: 237/255, blue: 227/255, alpha: 1),
                    lineWidth: 3
                )

                let innerPad = CGFloat(width) * 0.02
                let innerRect = NSRect(
                    x: outerRect.minX + innerPad,
                    y: outerRect.minY + innerPad,
                    width: outerRect.width - innerPad * 2,
                    height: outerRect.height - innerPad * 2
                )
                drawRoundedRect(
                    innerRect,
                    radius: CGFloat(width) * 0.025,
                    fill: NSColor(calibratedRed: 244/255, green: 248/255, blue: 245/255, alpha: 1)
                )

                let sourceSize = screenshot.size
                let scale = min(innerRect.width / sourceSize.width, innerRect.height / sourceSize.height)
                let drawW = sourceSize.width * scale
                let drawH = sourceSize.height * scale
                let drawRect = NSRect(
                    x: innerRect.midX - drawW / 2,
                    y: innerRect.midY - drawH / 2,
                    width: drawW,
                    height: drawH
                )
                screenshot.draw(in: drawRect)
            }

            let indexLabel = String(format: "%02d", index + 1)
            let fileName = "play_asset_screenshot_\(indexLabel)_\(item.slug)_\(target.name)_\(target.width)x\(target.height).png"
            let outPath = "\(finalScreenshotsDir)/\(fileName)"
            try writePng(rep, to: outPath)
            print(outPath)
        }
    }
}

try drawFeatureGraphic()
try generateTabletScreenshots()
