package com.example.cargotracker.booking.infrastructure.crypto;

import com.example.cargotracker.shared.contract.event.ShipperRegisteredEvent;
import java.lang.reflect.Type;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.conversion.Converter;

/**
 * 個人情報を<b>イベントのシリアライズ時</b>に暗号化する（ADR-0003 決定 1）。
 *
 * <p>暗号化をここに置く理由。Controller や集約で暗号化すると、暗号文が値オブジェクトの
 * 検査（メールの形など）に落ちるか、検査そのものが無意味になる。ドメインは平文を見て
 * 判断し、Event Store には暗号文だけが入る、という分担にする。</p>
 *
 * <p>復号もここで行うので、投影や問い合わせは平文を受け取る。鍵が破棄されていれば
 * {@code null} が渡り、投影の個人情報列が NULL になる。</p>
 */
public class ShipperDataEncryptingConverter implements Converter {

    private final Converter delegate;
    private final ShipperDataCipher cipher;

    public ShipperDataEncryptingConverter(Converter delegate, ShipperDataCipher cipher) {
        this.delegate = delegate;
        this.cipher = cipher;
    }

    @Override
    public <T> T convert(Object input, Type targetType) {
        if (input instanceof ShipperRegisteredEvent event && !isSameType(targetType, input)) {
            return delegate.convert(encrypt(event), targetType);
        }
        T converted = delegate.convert(input, targetType);
        if (converted instanceof ShipperRegisteredEvent event) {
            @SuppressWarnings("unchecked")
            T decrypted = (T) decrypt(event);
            return decrypted;
        }
        return converted;
    }

    private static boolean isSameType(Type targetType, Object input) {
        return targetType instanceof Class<?> clazz && clazz.isInstance(input);
    }

    private ShipperRegisteredEvent encrypt(ShipperRegisteredEvent e) {
        String id = e.shipperId();
        return new ShipperRegisteredEvent(id, e.shipperType(),
                cipher.encrypt(id, e.name()),
                cipher.encrypt(id, e.email()),
                cipher.encrypt(id, e.phone()),
                cipher.encrypt(id, e.address()),
                e.contractNumber(), e.discountRate());
    }

    private ShipperRegisteredEvent decrypt(ShipperRegisteredEvent e) {
        String id = e.shipperId();
        return new ShipperRegisteredEvent(id, e.shipperType(),
                cipher.decrypt(id, e.name()),
                cipher.decrypt(id, e.email()),
                cipher.decrypt(id, e.phone()),
                cipher.decrypt(id, e.address()),
                e.contractNumber(), e.discountRate());
    }

    @Override
    public void describeTo(ComponentDescriptor descriptor) {
        descriptor.describeWrapperOf(delegate);
    }
}
